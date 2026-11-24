"""Draft bug reports and feature requests to Plain Text Office.

- Mirror the iOS ``Feedback.swift`` and Android ``Feedback.kt`` screens: the
  email draft carries only a subject and a short app and system summary, and
  nothing is sent unless the user sends it from their email app.
- Never add documents, file paths, settings, or other user content.
- Depend only on the standard library and PySide6 so the same file can be
  copied unchanged into every Plain Text Office Windows app.
"""

from __future__ import annotations

import ctypes
import platform
import sys
from enum import Enum
from importlib import metadata
from pathlib import Path
from typing import cast
from urllib.parse import quote

from PySide6.QtCore import QUrl, qVersion
from PySide6.QtGui import QAction, QDesktopServices
from PySide6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QLabel,
    QMenu,
    QPushButton,
    QToolBar,
    QVBoxLayout,
    QWidget,
)

FEEDBACK_ADDRESS = "feedback@plaintxtoffice.com"
CONTACT_URL = "https://plaintxtoffice.com/contact/"

_EMAIL_FAILURE = (
    "No email app is set up on this computer. Use Open Contact Form instead."
)
_CONTACT_FAILURE = (
    "The contact form couldn't be opened. Use Report a Bug or Request a "
    "Feature to write an email instead."
)
_INTRO = (
    "Found a problem or have an idea? Report a Bug and Request a Feature open "
    "a draft in your email app, addressed to Plain Text Office, with your app "
    "version and Windows version filled in. Open Contact Form opens the "
    "contact form in your browser. Nothing is sent unless you send it."
)
_BUG_HINT = "For a bug, include what you did and what happened."
_WORD_BITS = 16
_WORD_MASK = 0xFFFF


class FeedbackKind(Enum):
    """Kind of feedback a drafted email is about."""

    BUG_REPORT = "bug report"
    FEATURE_REQUEST = "feature request"


def feedback_subject(app_name: str, kind: FeedbackKind) -> str:
    """Return the draft subject, such as ``Plain Todo bug report (Windows)``."""
    return f"{app_name} {kind.value} (Windows)"


def feedback_body(summary: str) -> str:
    """Return blank lines for the user's message, ending with the summary."""
    return "\n\n" + summary


def format_system_summary(
    *,
    app_name: str,
    version: str,
    windows_release: str,
    windows_build: str,
    runtime: str,
) -> str:
    """Return the three-line app, Windows, and runtime summary."""
    return (
        f"{app_name} {version}\n"
        f"Windows {windows_release} ({windows_build})\n"
        f"{runtime}"
    )


def system_summary(app_name: str, distribution: str) -> str:
    """Return app and system facts that help reproduce a report."""
    release, build, _, _ = platform.win32_ver()
    return format_system_summary(
        app_name=app_name,
        version=app_version(distribution),
        windows_release=release or platform.release(),
        windows_build=build or platform.version(),
        runtime=f"{platform.machine()}, Qt {qVersion()}",
    )


def feedback_mailto(app_name: str, kind: FeedbackKind, summary: str) -> str:
    """Return a ``mailto:`` URL that drafts feedback to Plain Text Office.

    - Percent-encode every reserved character and use CRLF line breaks in the
      body, as RFC 6068 requires.
    """
    subject = quote(feedback_subject(app_name, kind), safe="")
    body_text = feedback_body(summary).replace("\n", "\r\n")
    body = quote(body_text, safe="")
    return f"mailto:{FEEDBACK_ADDRESS}?subject={subject}&body={body}"


def app_version(distribution: str) -> str:
    """Return the installed version of ``distribution``.

    - Fall back to the product version of the packaged executable, which the
      Nuitka build sets from ``pyproject.toml``, when package metadata is not
      bundled.
    - Return ``unknown`` when neither source is available.
    """
    try:
        return metadata.version(distribution)
    except metadata.PackageNotFoundError:
        pass
    if "__compiled__" in globals():
        for candidate in (sys.argv[0], sys.executable):
            version = executable_version(Path(candidate))
            if version:
                return version
    return "unknown"


class _FixedFileInfo(ctypes.Structure):
    """Mirror the Win32 ``VS_FIXEDFILEINFO`` structure."""

    _fields_ = (
        ("signature", ctypes.c_uint32),
        ("struct_version", ctypes.c_uint32),
        ("file_version_ms", ctypes.c_uint32),
        ("file_version_ls", ctypes.c_uint32),
        ("product_version_ms", ctypes.c_uint32),
        ("product_version_ls", ctypes.c_uint32),
    )


def executable_version(path: Path) -> str | None:
    """Return the product version stored in a Windows executable.

    - Drop a trailing zero fourth part, so ``1.0.0.0`` reads ``1.0.0``.
    - Return None off Windows, for missing files, and for files without
      version information.
    """
    if sys.platform != "win32" or not path.is_file():
        return None
    version_dll = ctypes.WinDLL("version")
    size = version_dll.GetFileVersionInfoSizeW(str(path), None)
    if not size:
        return None
    data = ctypes.create_string_buffer(size)
    if not version_dll.GetFileVersionInfoW(str(path), 0, size, data):
        return None
    pointer = ctypes.c_void_p()
    length = ctypes.c_uint()
    if not version_dll.VerQueryValueW(
        data, "\\", ctypes.byref(pointer), ctypes.byref(length)
    ):
        return None
    info = ctypes.cast(pointer, ctypes.POINTER(_FixedFileInfo)).contents
    parts = [
        info.product_version_ms >> _WORD_BITS,
        info.product_version_ms & _WORD_MASK,
        info.product_version_ls >> _WORD_BITS,
        info.product_version_ls & _WORD_MASK,
    ]
    if parts[-1] == 0:
        parts.pop()
    return ".".join(str(part) for part in parts)


def open_feedback_email(
    app_name: str, distribution: str, kind: FeedbackKind
) -> bool:
    """Open a feedback draft in the user's email app.

    - Return False when no email app accepts the draft.
    """
    summary = system_summary(app_name, distribution)
    url = feedback_mailto(app_name, kind, summary)
    return QDesktopServices.openUrl(QUrl(url, QUrl.ParsingMode.StrictMode))


def open_contact_form() -> bool:
    """Open the contact form in the browser; return False when it fails."""
    return QDesktopServices.openUrl(QUrl(CONTACT_URL))


class FeedbackDialog(QDialog):
    """Offer a bug report, a feature request, or the contact form.

    - Close after the email app or browser accepts the request; otherwise
      keep the dialog open and show what to try instead.
    """

    def __init__(
        self, app_name: str, distribution: str, parent: QWidget | None = None
    ) -> None:
        """Build the dialog for the named app and its package distribution."""
        super().__init__(parent)
        self._app_name = app_name
        self._distribution = distribution
        self.setWindowTitle("Send Feedback")
        layout = QVBoxLayout(self)
        intro = QLabel(_INTRO, self)
        intro.setWordWrap(True)
        layout.addWidget(intro)
        hint = QLabel(_BUG_HINT, self)
        hint.setWordWrap(True)
        hint.setEnabled(False)
        layout.addWidget(hint)
        layout.addWidget(
            self._button(
                "Report a &Bug",
                "feedbackReportBugButton",
                FeedbackKind.BUG_REPORT,
            )
        )
        layout.addWidget(
            self._button(
                "Request a &Feature",
                "feedbackRequestFeatureButton",
                FeedbackKind.FEATURE_REQUEST,
            )
        )
        contact = QPushButton("Open &Contact Form", self)
        contact.setObjectName("feedbackContactFormButton")
        contact.clicked.connect(self._open_contact_form)
        layout.addWidget(contact)
        self.error_label = QLabel(self)
        self.error_label.setObjectName("feedbackError")
        self.error_label.setWordWrap(True)
        self.error_label.setStyleSheet("color: #c42b1c;")
        self.error_label.hide()
        layout.addWidget(self.error_label)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close, self)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def _button(
        self, label: str, name: str, kind: FeedbackKind
    ) -> QPushButton:
        button = QPushButton(label, self)
        button.setObjectName(name)
        button.clicked.connect(lambda: self._send_email(kind))
        return button

    def _send_email(self, kind: FeedbackKind) -> None:
        opened = open_feedback_email(self._app_name, self._distribution, kind)
        self._finish(opened=opened, failure=_EMAIL_FAILURE)

    def _open_contact_form(self) -> None:
        self._finish(opened=open_contact_form(), failure=_CONTACT_FAILURE)

    def _finish(self, *, opened: bool, failure: str) -> None:
        if opened:
            self.error_label.hide()
            self.accept()
            return
        self.error_label.setText(failure)
        self.error_label.show()


def show_feedback_dialog(
    parent: QWidget | None, app_name: str, distribution: str
) -> None:
    """Show the feedback dialog modally and release it afterwards."""
    dialog = FeedbackDialog(app_name, distribution, parent)
    dialog.exec()
    dialog.deleteLater()


def add_feedback_action(
    target: QMenu | QToolBar,
    parent: QWidget,
    app_name: str,
    distribution: str,
) -> QAction:
    """Add a ``Send Feedback...`` action that opens the feedback dialog.

    - Look the parent up when triggered; a slot that captured it would keep
      the parent alive after its window closes.
    """
    action = QAction("Send &Feedback...", parent)
    action.setObjectName("sendFeedbackAction")
    action.triggered.connect(
        lambda: show_feedback_dialog(
            cast("QWidget", action.parent()), app_name, distribution
        )
    )
    target.addAction(action)
    return action
