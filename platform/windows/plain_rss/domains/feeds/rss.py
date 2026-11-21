"""Feed retrieval, parsing, and subscription persistence services."""

from __future__ import annotations

import json
import logging
import os
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from html.parser import HTMLParser
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any
from urllib.error import URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

import defusedxml.ElementTree
from defusedxml.common import DefusedXmlException

LOGGER = logging.getLogger(__name__)
FETCH_TIMEOUT_SECONDS = 15
USER_AGENT = "Plain RSS/0.1"
ISO_DATE_LENGTH = 10
type XmlElement = Any


class FeedFetchError(Exception):
    """Raise when a feed cannot be downloaded or parsed."""


class _ImageStrippingParser(HTMLParser):
    """Rebuild summary HTML without embedded image elements."""

    def __init__(self) -> None:
        """Initialize the HTML output buffer."""
        super().__init__(convert_charrefs=False)
        self.html = ""

    def handle_starttag(
        self,
        tag: str,
        attrs: list[tuple[str, str | None]],
    ) -> None:
        """Preserve non-image opening tags."""
        del attrs
        if tag.lower() != "img":
            self.html += self.get_starttag_text() or f"<{tag}>"

    def handle_startendtag(
        self,
        tag: str,
        attrs: list[tuple[str, str | None]],
    ) -> None:
        """Preserve non-image self-closing tags."""
        del attrs
        if tag.lower() != "img":
            self.html += self.get_starttag_text() or f"<{tag} />"

    def handle_endtag(self, tag: str) -> None:
        """Preserve non-image closing tags."""
        if tag.lower() != "img":
            self.html += f"</{tag}>"

    def handle_data(self, data: str) -> None:
        """Preserve summary text."""
        self.html += data

    def handle_entityref(self, name: str) -> None:
        """Preserve named HTML entities."""
        self.html += f"&{name};"

    def handle_charref(self, name: str) -> None:
        """Preserve numeric HTML entities."""
        self.html += f"&#{name};"


@dataclass(frozen=True, slots=True)
class Subscription:
    """A feed URL saved by the reader."""

    title: str
    url: str


@dataclass(frozen=True, slots=True)
class FeedEntry:
    """A normalized RSS or Atom article with an ISO publication date."""

    feed_title: str
    title: str
    link: str
    summary: str
    published: str | None


@dataclass(frozen=True, slots=True)
class ParsedFeed:
    """A feed and its normalized entries."""

    title: str
    entries: tuple[FeedEntry, ...]


class SubscriptionRepository:
    """Persist subscriptions in a local JSON file."""

    def __init__(self, path: Path) -> None:
        """Initialize the repository at ``path``."""
        self._path = path

    def load(self) -> list[Subscription]:
        """Return saved subscriptions, or an empty list on first launch."""
        if not self._path.exists():
            return []

        try:
            raw_subscriptions = json.loads(
                self._path.read_text(encoding="utf-8"),
            )
        except (OSError, json.JSONDecodeError) as error:
            LOGGER.warning("Could not load subscriptions: %s", error)
            return []

        if not isinstance(raw_subscriptions, list):
            LOGGER.warning("Subscription file did not contain a list.")
            return []

        subscriptions: list[Subscription] = []
        for record in raw_subscriptions:
            if not isinstance(record, dict):
                continue
            title = record.get("title")
            url = record.get("url")
            if isinstance(title, str) and isinstance(url, str) and url:
                subscriptions.append(Subscription(title=title, url=url))
        return subscriptions

    def save(self, subscriptions: list[Subscription]) -> None:
        """Write subscriptions to disk."""
        self._write_export(self._path, subscriptions)

    def write_export(
        self,
        path: Path,
        subscriptions: list[Subscription],
    ) -> None:
        """Write subscriptions to a user-selected JSON export path."""
        self._write_export(path, subscriptions)

    @staticmethod
    def _write_export(path: Path, subscriptions: list[Subscription]) -> None:
        """Write subscriptions atomically to ``path``."""
        path.parent.mkdir(parents=True, exist_ok=True)
        contents = json.dumps(
            [asdict(subscription) for subscription in subscriptions],
            indent=2,
            sort_keys=True,
        )
        temporary_path = SubscriptionRepository._write_temporary_contents(
            path,
            contents,
        )
        try:
            temporary_path.replace(path)
        except OSError:
            temporary_path.unlink(missing_ok=True)
            raise

    @staticmethod
    def _write_temporary_contents(path: Path, contents: str) -> Path:
        """Write and flush subscription JSON beside its final destination."""
        with NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary_file:
            temporary_path = Path(temporary_file.name)
            temporary_file.write(f"{contents}\n")
            temporary_file.flush()
            os.fsync(temporary_file.fileno())
        return temporary_path


class FeedFetcher:
    """Download and parse RSS and Atom feeds."""

    @staticmethod
    def fetch(url: str) -> ParsedFeed:
        """Fetch and parse the feed at ``url``."""
        if not is_web_url(url):
            msg = "Feed URLs must use http:// or https:// with a host."
            raise FeedFetchError(msg)

        # `is_web_url` rejects file and custom schemes before these calls.
        request = Request(  # noqa: S310
            url,
            headers={"User-Agent": USER_AGENT},
        )
        try:
            with urlopen(  # noqa: S310
                request,
                timeout=FETCH_TIMEOUT_SECONDS,
            ) as response:
                payload = response.read()
        except (OSError, URLError) as error:
            msg = f"Could not download {url}: {error}"
            raise FeedFetchError(
                msg,
            ) from error

        try:
            return parse_feed(payload)
        except (
            DefusedXmlException,
            defusedxml.ElementTree.ParseError,
        ) as error:
            msg = f"{url} did not return a valid RSS or Atom feed."
            raise FeedFetchError(
                msg,
            ) from error


def parse_feed(payload: bytes) -> ParsedFeed:
    """Normalize an RSS 2.0 or Atom XML document into a parsed feed."""
    root = defusedxml.ElementTree.fromstring(payload)
    is_rss = _local_name(root.tag) == "rss"
    feed_element = _first_child(root, "channel") if is_rss else root
    if feed_element is None:
        msg = "RSS document has no channel element"
        raise defusedxml.ElementTree.ParseError(msg)

    feed_title = _child_text(feed_element, "title") or "Untitled feed"
    entry_name = "item" if is_rss else "entry"
    entries = tuple(
        _parse_entry(element, feed_title)
        for element in feed_element
        if _local_name(element.tag) == entry_name
    )
    return ParsedFeed(title=feed_title, entries=entries)


def _parse_entry(element: XmlElement, feed_title: str) -> FeedEntry:
    """Create a normalized entry from an RSS item or Atom entry."""
    summary = _child_text(
        element,
        "encoded",
        "content",
        "summary",
        "description",
    )
    return FeedEntry(
        feed_title=feed_title,
        title=_child_text(element, "title") or "Untitled article",
        link=_entry_link(element),
        summary=_strip_summary_images(summary),
        published=_normalize_published(
            _child_text(element, "pubDate", "published", "updated"),
        ),
    )


def _entry_link(element: XmlElement) -> str:
    """Return the preferred link from an RSS item or Atom entry."""
    for child in element:
        if _local_name(child.tag) != "link":
            continue
        if child.get("rel") in {None, "alternate"} and child.get("href"):
            return child.get("href", "")
        if child.text and child.text.strip():
            return child.text.strip()
    return ""


def _child_text(element: XmlElement, *names: str) -> str:
    """Return the first nonempty direct child text matching ``names``."""
    for name in names:
        for child in element:
            if _local_name(child.tag) != name:
                continue
            value = "".join(child.itertext()).strip()
            if value:
                return value
    return ""


def _first_child(
    element: XmlElement,
    name: str,
) -> XmlElement | None:
    """Return a direct child by local XML tag name."""
    return next(
        (child for child in element if _local_name(child.tag) == name),
        None,
    )


def _local_name(tag: str) -> str:
    """Strip an XML namespace from a tag name."""
    return tag.rsplit("}", maxsplit=1)[-1]


def _strip_summary_images(summary: str) -> str:
    """Remove image tags from feed-provided summary HTML."""
    parser = _ImageStrippingParser()
    parser.feed(summary)
    parser.close()
    return parser.html


def _normalize_published(value: str) -> str | None:
    """Return an RSS or Atom publication value as ISO 8601 when possible."""
    if not value:
        return None
    if len(value) == ISO_DATE_LENGTH:
        try:
            return datetime.fromisoformat(value).date().isoformat()
        except ValueError:
            return None

    try:
        timestamp = datetime.fromisoformat(
            f"{value[:-1]}+00:00" if value.endswith("Z") else value,
        )
    except ValueError:
        try:
            timestamp = parsedate_to_datetime(value)
        except (TypeError, ValueError, IndexError):
            return None

    if timestamp.tzinfo is None:
        timestamp = timestamp.replace(tzinfo=UTC)
    else:
        timestamp = timestamp.astimezone(UTC)
    return timestamp.isoformat()


def is_web_url(value: str) -> bool:
    """Return whether ``value`` is an HTTP(S) URL with a host."""
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)
