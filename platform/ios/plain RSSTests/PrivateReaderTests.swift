import XCTest
import Network
import WebKit
@testable import Open_RSS

nonisolated final class ReaderFixtureServer: @unchecked Sendable {
    let listener: NWListener
    private let lock = NSLock()
    private var recorded: [String] = []
    var requests: [String] { lock.lock(); defer { lock.unlock() }; return recorded }
    init() throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in
            connection.start(queue: .global())
            connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { data, _, _, _ in
                guard let self, let data, let request = String(data: data, encoding: .utf8) else { connection.cancel(); return }
                let path = request.split(separator: " ").dropFirst().first.map(String.init) ?? "?"
                self.lock.lock(); self.recorded.append(path); self.lock.unlock()
                print("RSS_FIXTURE_REQUEST \(path)")
                let png = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")!
                var status = "200 OK"; var type = "image/png"; var headers = ""; var body = png
                if path == "/redirect" { status = "302 Found"; headers = "Location: /image.png\r\n"; body = Data() }
                if path == "/unsafe-redirect" { status = "302 Found"; headers = "Location: file:///tmp/forbidden\r\n"; body = Data() }
                if path == "/not-image" { type = "text/html"; body = Data("<script>location='/leak'</script>".utf8) }
                let response = Data("HTTP/1.1 \(status)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\n\(headers)Connection: close\r\n\r\n".utf8) + body
                let send: @Sendable () -> Void = { connection.send(content: response, completion: .contentProcessed { _ in connection.cancel() }) }
                if path == "/slow.png" { DispatchQueue.global().asyncAfter(deadline: .now() + 2, execute: send) } else { send() }
            }
        }
    }
    func start() async throws -> URL {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<URL, Error>) in
            listener.stateUpdateHandler = { [weak self] state in
                guard let self else { return }
                switch state {
                case .ready: continuation.resume(returning: URL(string: "http://127.0.0.1:\(self.listener.port!.rawValue)/article")!)
                case .failed(let error): continuation.resume(throwing: error)
                default: break
                }
            }
            listener.start(queue: .global())
        }
    }
    func stop() { listener.cancel() }
}

@MainActor final class PrivateReaderTests: XCTestCase {
    func input(_ body: String, base: URL? = nil, images: Bool = false) -> ReaderInput {
        ReaderInput(title: "Synthetic article", metadata: "Fixture", body: body, baseURL: base, allowsImages: images)
    }
    func render(_ session: PrivateReaderSession, _ input: ReaderInput) async {
        let finished = expectation(description: "Local private document rendered")
        session.didFinish = { finished.fulfill() }
        session.load(input)
        await fulfillment(of: [finished], timeout: 15)
        session.didFinish = nil
    }
    func testStrictSanitizerAndUnsafeURLs() {
        let base = URL(string: "https://example.invalid/article")!
        let raw = """
        <p style="background:url('/background')" onclick="steal()">Hello <b>world</b></p>
        <script src='/script'>fetch('/leak')</script><iframe src='/frame'></iframe>
        <style>@import '/css';</style><svg><image href='/svg'></image></svg>
        <form action='/form'><input autofocus onfocus='steal()'></form>
        <meta http-equiv='refresh' content='0;url=/redirect'><link rel='preload' href='/preload'>
        <img src='/image.png' srcset='/srcset 2x' style='background:url(/bg)' onerror='steal()' alt='Diagram'>
        <a href='java&#x73;cript:alert(1)'>Bad</a><a href='/good' ping='/ping' target='_blank'>Good</a>
        <a href='file:///etc/passwd'>File</a><a href='data:text/html,evil'>Data</a>
        """
        let off = ReaderSanitizer.document(input(raw, base: base))
        XCTAssertTrue(off.images.isEmpty); XCTAssertEqual(off.links.count, 1)
        XCTAssertTrue(off.html.contains("Hello <b>world</b>"))
        for forbidden in ["onclick", "onerror", "srcset", "<iframe", "<script", "<svg", "<form", "@import", "ping=", "https://example.invalid", "file:///", "javascript:"] { XCTAssertFalse(off.html.contains(forbidden), forbidden) }
        let on = ReaderSanitizer.document(input(raw, base: base, images: true))
        XCTAssertEqual(on.images.count, 1); XCTAssertEqual(on.images.values.first?.path, "/image.png")
        for url in ["javascript:alert(1)", "data:image/png,x", "file:///tmp/x", "mailto:a@b.com", "https://user:pass@example.invalid/", "https://example.invalid/\n"] { XCTAssertNil(ReaderURL.allowed(url)) }
        for raw in ["<img src='https://example.invalid/unclosed", "<!--<img src='https://example.invalid/x'>", "<STYLE><img src='https://example.invalid/x'>", "<svg/onload=alert(1)><image href='https://example.invalid/x'></svg>"] { XCTAssertTrue(ReaderSanitizer.document(input(raw, images: true)).images.isEmpty) }
    }
    func testNetworkDefaultOptInResetAndRepeatedRender() async throws {
        let server = try ReaderFixtureServer(); let base = try await server.start(); defer { server.stop() }
        let raw = """
        <p style="background:url('/background')">Readable <strong>text</strong></p>
        <script src='/script'></script><style>@import '/css';@font-face{src:url('/font')}</style>
        <iframe src='/frame'></iframe><video src='/media' poster='/poster'></video>
        <meta http-equiv='refresh' content='0;url=/automatic'><link rel='preload' href='/preload'>
        <svg><image href='/svg'></svg><form action='/form'><input></form>
        <img src='/image.png' srcset='/srcset 2x' onerror='location="/error"'>
        <a href='/tapped' ping='/ping'>External link</a>
        """
        let session = PrivateReaderSession(openExternal: { _ in XCTFail("No automatic browser opening") })
        defer { session.cancel() }
        XCTAssertFalse(session.webView.configuration.websiteDataStore.isPersistent)
        await render(session, input(raw, base: base))
        try await Task.sleep(for: .milliseconds(600))
        XCTAssertEqual(server.requests, [])
        print("RSS_NETWORK_EVIDENCE default requests=\(server.requests)")
        await render(session, input(raw, base: base, images: true))
        XCTAssertEqual(server.requests, ["/image.png"])
        let loadedImages = try await session.webView.evaluateJavaScript("document.images.length") as? Int
        XCTAssertEqual(loadedImages, 1)
        let imageWidth = try await session.webView.evaluateJavaScript("document.images[0].naturalWidth") as? Int
        XCTAssertEqual(imageWidth, 1)
        session.load(input(raw, base: base, images: true))
        try await Task.sleep(for: .milliseconds(300))
        XCTAssertEqual(server.requests, ["/image.png"])
        let link = try XCTUnwrap(session.document?.links.keys.first)
        XCTAssertNil(session.destination(for: link, explicitlyTapped: false))
        XCTAssertEqual(session.destination(for: link, explicitlyTapped: true)?.path, "/tapped")
        XCTAssertNil(session.destination(for: base, explicitlyTapped: true))
        let oldImage = try XCTUnwrap(session.document?.images.keys.first)
        await render(session, input("<p>Another article</p><img src='/second.png'>", base: base))
        XCTAssertTrue(session.document?.images.isEmpty == true)
        XCTAssertNil(session.document?.images[oldImage]); XCTAssertNil(session.destination(for: link, explicitlyTapped: true))
        XCTAssertEqual(server.requests, ["/image.png"])
        print("RSS_NETWORK_EVIDENCE opt-in/reset requests=\(server.requests)")
    }
    func testImageValidationRedirectsAndInterruptedNavigation() async throws {
        let server = try ReaderFixtureServer(); let base = try await server.start(); defer { server.stop() }
        let data = try await ReaderImageFetcher.fetch(base.deletingLastPathComponent().appendingPathComponent("redirect"))
        XCTAssertFalse(data.isEmpty); XCTAssertEqual(server.requests, ["/redirect", "/image.png"])
        for path in ["not-image", "unsafe-redirect"] {
            do { _ = try await ReaderImageFetcher.fetch(base.deletingLastPathComponent().appendingPathComponent(path)); XCTFail("Invalid resource accepted") } catch {}
        }
        let session = PrivateReaderSession(); defer { session.cancel() }
        session.load(input("<img src='/slow.png'>", base: base, images: true))
        for _ in 0..<100 { if server.requests.contains("/slow.png") { break }; try await Task.sleep(for: .milliseconds(50)) }
        XCTAssertTrue(server.requests.contains("/slow.png"))
        await render(session, input("<p>Interrupted and replaced</p>", base: base))
        try await Task.sleep(for: .milliseconds(2300))
        XCTAssertTrue(session.document?.images.isEmpty == true)
        session.cancel()
        print("RSS_NETWORK_EVIDENCE redirect/validation/interruption requests=\(server.requests)")
    }
}
