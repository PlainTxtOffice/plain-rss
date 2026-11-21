"""Tests for RSS and Atom feed services."""

from pathlib import Path
from unittest.mock import patch

import pytest
from defusedxml.common import DefusedXmlException

from plain_rss.domains.feeds.rss import (
    FeedFetcher,
    FeedFetchError,
    Subscription,
    SubscriptionRepository,
    parse_feed,
)


def test_parse_rss_feed() -> None:
    """RSS items are normalized into reader entries."""
    feed = parse_feed(
        b"""<?xml version="1.0"?>
        <rss version="2.0"><channel><title>Example News</title>
        <item><title>First story</title><link>https://example.com/first</link>
        <description>Story summary</description>
        <pubDate>Sun, 12 Jul 2026 14:23:07 +0000</pubDate></item>
        </channel></rss>""",
    )

    assert feed.title == "Example News"
    assert feed.entries[0].title == "First story"
    assert feed.entries[0].link == "https://example.com/first"
    assert feed.entries[0].summary == "Story summary"
    assert feed.entries[0].published == "2026-07-12T14:23:07+00:00"


def test_parse_atom_feed() -> None:
    """Atom entries with XML namespaces are normalized into reader entries."""
    feed = parse_feed(
        b"""<?xml version="1.0"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Example Atom</title>
        <entry><title>Atom story</title><link href="https://example.com/atom" />
        <summary>Atom summary</summary><updated>2026-07-15</updated></entry>
        </feed>""",
    )

    assert feed.title == "Example Atom"
    assert feed.entries[0].link == "https://example.com/atom"
    assert feed.entries[0].published == "2026-07-15"


@pytest.mark.parametrize(
    ("published", "expected"),
    [
        ("2026-07-15T14:23:00Z", "2026-07-15T14:23:00+00:00"),
        ("2026-07-15T14:23:00", "2026-07-15T14:23:00+00:00"),
        ("Sun, 12 Jul 2026 14:23:07 +0000", "2026-07-12T14:23:07+00:00"),
        ("not a date", None),
    ],
)
def test_parse_feed_normalizes_publication_values(
    published: str,
    expected: str | None,
) -> None:
    """Supported feed dates become ISO values while invalid dates become None."""
    feed = parse_feed(
        f"""<?xml version="1.0"?>
        <rss version="2.0"><channel><title>Example News</title>
        <item><title>Story</title><pubDate>{published}</pubDate></item>
        </channel></rss>""".encode(),
    )

    assert feed.entries[0].published == expected


def test_parse_atom_feed_prefers_alternate_link() -> None:
    """Atom self links do not replace the article's alternate URL."""
    feed = parse_feed(
        b"""<?xml version="1.0"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Example Atom</title>
        <entry><title>Atom story</title>
        <link rel="self" href="https://example.com/feed" />
        <link rel="alternate" href="https://example.com/article" />
        </entry></feed>""",
    )

    assert feed.entries[0].link == "https://example.com/article"


def test_parse_feed_removes_summary_images() -> None:
    """Summary text and links remain after embedded images are removed."""
    feed = parse_feed(
        b"""<?xml version="1.0"?>
        <rss version="2.0"><channel><title>Example News</title>
        <item><title>Story</title><description><![CDATA[
        <p>Read <a href="https://example.com">more</a>.
        <img src="https://example.com/thumbnail.jpg" alt="Thumbnail"></p>
        ]]></description></item></channel></rss>""",
    )

    summary = feed.entries[0].summary
    assert "<img" not in summary
    assert "Read <a href=\"https://example.com\">more</a>." in summary


def test_parse_feed_prefers_encoded_content() -> None:
    """RSS content:encoded takes precedence over a shorter description."""
    feed = parse_feed(
        b"""<?xml version="1.0"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
        <channel><title>Example News</title><item><title>Story</title>
        <description>Short description</description>
        <content:encoded><![CDATA[Full article preview]]></content:encoded>
        </item></channel></rss>""",
    )

    assert feed.entries[0].summary == "Full article preview"


def test_parse_feed_rejects_xml_entity_declaration() -> None:
    """Unsafe DTD entity declarations are rejected before parsing feeds."""
    unsafe_feed = b"""<?xml version="1.0"?>
    <!DOCTYPE rss [<!ENTITY unsafe "blocked">]>
    <rss><channel><title>&unsafe;</title></channel></rss>"""

    with pytest.raises(DefusedXmlException):
        parse_feed(unsafe_feed)


def test_fetch_rejects_non_web_urls_before_opening() -> None:
    """Local-file URLs are rejected before a request can open them."""
    with patch("plain_rss.domains.feeds.rss.urlopen") as mocked_urlopen:
        with pytest.raises(FeedFetchError, match="http:// or https://"):
            FeedFetcher.fetch("file:///private/feed.xml")

    mocked_urlopen.assert_not_called()


def test_subscription_repository_round_trips(tmp_path: Path) -> None:
    """Saved subscriptions are available on the next application launch."""
    repository = SubscriptionRepository(tmp_path / "subscriptions.json")
    subscriptions = [Subscription(title="Example", url="https://example.com/rss")]

    repository.save(subscriptions)

    assert repository.load() == subscriptions
    assert not list(tmp_path.glob(".subscriptions.json.*.tmp"))


def test_subscription_repository_writes_export(tmp_path: Path) -> None:
    """Subscription exports use the same format as saved subscriptions."""
    repository = SubscriptionRepository(tmp_path / "subscriptions.json")
    export_path = tmp_path / "backup.json"
    subscriptions = [Subscription(title="Example", url="https://example.com/rss")]

    repository.write_export(export_path, subscriptions)

    assert SubscriptionRepository(export_path).load() == subscriptions
    assert not list(tmp_path.glob(".backup.json.*.tmp"))