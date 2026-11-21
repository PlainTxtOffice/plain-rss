"""Settings dialog for the RSS reader root window."""

from __future__ import annotations

from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
)

from plain_rss.config.settings import (
    DATE_DISPLAY_FORMATS,
    DateDisplayFormat,
    normalize_date_display_format,
)

_DATE_FORMAT_LABELS = {
    "month_day_12_hour": "Jul 15, 2026 2:23 PM",
    "month_day_24_hour": "Jul 15, 2026 14:23",
    "day_month_24_hour": "15 Jul 2026 14:23",
    "iso_24_hour": "2026-07-15 14:23",
}


class SettingsDialog(QDialog):
    """Let a reader choose persistent display preferences."""

    def __init__(self, date_display_format: DateDisplayFormat) -> None:
        """Create the settings dialog with the saved format selected."""
        super().__init__()
        self.setWindowTitle("Settings")
        layout = QFormLayout(self)

        self._date_display_combo = QComboBox()
        for display_format in DATE_DISPLAY_FORMATS:
            self._date_display_combo.addItem(
                _DATE_FORMAT_LABELS[display_format],
                display_format,
            )
        selected_index = self._date_display_combo.findData(
            date_display_format,
        )
        self._date_display_combo.setCurrentIndex(selected_index)
        layout.addRow("Date display:", self._date_display_combo)

        button_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Cancel
            | QDialogButtonBox.StandardButton.Ok,
        )
        button_box.accepted.connect(self.accept)
        button_box.rejected.connect(self.reject)
        layout.addRow(button_box)

    def date_display_format(self) -> DateDisplayFormat:
        """Return the selected semantic date-display preference."""
        selected_format = self._date_display_combo.currentData()
        return normalize_date_display_format(selected_format)
