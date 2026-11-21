import SwiftUI
import WebKit
import ImageIO

/// Only these URL schemes can ever leave the reader, and only after an explicit tap/permission.
nonisolated enum ReaderURL {
    static func allowed(_ value: String, relativeTo base: URL? = nil) -> URL? {
        guard !value.unicodeScalars.contains(where: { $0.value < 32 || $0.value == 127 }),
              let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines), relativeTo: base)?.absoluteURL,
              ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
              url.host?.isEmpty == false, url.user == nil, url.password == nil else { return nil }
        return url
    }
}

nonisolated struct ReaderInput: Equatable {
    var title: String
    var metadata: String
    var body: String
    var baseURL: URL?
    var allowsImages: Bool
}

nonisolated struct ReaderDocument {
    var html: String
    var images: [URL: URL]
    var links: [URL: URL]
}

/// Reconstructs a small HTML vocabulary. No publisher CSS, attributes, executable markup,
/// namespaces, raw text elements, URLs or document controls enter the generated document.
nonisolated enum ReaderSanitizer {
    static let formatting: Set<String> = ["p", "div", "span", "br", "hr", "strong", "b", "em", "i", "u", "s", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "code", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "figure", "figcaption", "a"]
    static let discarded: Set<String> = ["script", "style", "iframe", "object", "embed", "svg", "math", "form", "video", "audio", "template", "noscript", "textarea", "title", "xmp", "plaintext"]
    static func escaped(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
    }
    // Deliberately small named-entity vocabulary; unknown entities remain harmless literal text.
    static func decoded(_ s: String) -> String {
        let chars = Array(s); var result = ""; var i = 0
        let named = ["amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": " ", "colon": ":", "Tab": "\t", "NewLine": "\n"]
        while i < chars.count {
            if chars[i] == "&", let end = chars[(i + 1)...].prefix(16).firstIndex(of: ";") {
                let name = String(chars[(i + 1)..<end]); var replacement = named[name] ?? String.decodeEntity(name).map(String.init)
                if name.hasPrefix("#") {
                    let hex = name.lowercased().hasPrefix("#x")
                    if let n = UInt32(name.dropFirst(hex ? 2 : 1), radix: hex ? 16 : 10), let scalar = UnicodeScalar(n) { replacement = String(scalar) }
                }
                if let replacement { result += replacement; i = end + 1; continue }
            }
            result.append(chars[i]); i += 1
        }
        return result
    }
    static func document(_ input: ReaderInput, generation: String = UUID().uuidString.lowercased()) -> ReaderDocument {
        var images: [URL: URL] = [:]; var links: [URL: URL] = [:]
        var output = ""; let c = Array(input.body.prefix(500_000)); var i = 0
        var suppressed: [String] = []
        while i < c.count {
            guard c[i] == "<" else {
                let start = i; while i < c.count && c[i] != "<" { i += 1 }
                if suppressed.isEmpty { output += escaped(decoded(String(c[start..<i]))) }
                continue
            }
            if String(c[i..<min(i + 4, c.count)]) == "<!--" {
                i += 4
                while i + 2 < c.count && String(c[i...i + 2]) != "-->" { i += 1 }
                i = min(c.count, i + 3); continue
            }
            // A '<' that does not start a tag, comment, or declaration ("a < b", "<3") is text.
            let lead = i + 1 < c.count && c[i + 1] == "/" ? i + 2 : i + 1
            guard lead < c.count, c[lead].isASCII, c[lead].isLetter || c[lead] == "!" || c[lead] == "?" else {
                if suppressed.isEmpty { output += "&lt;" }
                i += 1; continue
            }
            i += 1; var closing = false
            if i < c.count && c[i] == "/" { closing = true; i += 1 }
            let nameStart = i
            while i < c.count && (c[i].isASCII && (c[i].isLetter || c[i].isNumber)) { i += 1 }
            let name = String(c[nameStart..<i]).lowercased()
            var attributes: [String: String] = [:]; var complete = false
            while i < c.count {
                while i < c.count && c[i].isWhitespace { i += 1 }
                if i >= c.count { break }
                if c[i] == ">" { i += 1; complete = true; break }
                if c[i] == "/" { i += 1; continue }
                let start = i
                while i < c.count && !c[i].isWhitespace && !["=", ">", "/"].contains(c[i]) { i += 1 }
                if i == start { i += 1; continue }
                let key = String(c[start..<i]).lowercased()
                while i < c.count && c[i].isWhitespace { i += 1 }
                var value = ""
                if i < c.count && c[i] == "=" {
                    i += 1; while i < c.count && c[i].isWhitespace { i += 1 }
                    if i < c.count && ["'", "\""].contains(c[i]) {
                        let quote = c[i]; i += 1; let start = i
                        while i < c.count && c[i] != quote { i += 1 }
                        value = String(c[start..<i]); if i < c.count { i += 1 }
                    } else {
                        let start = i
                        while i < c.count && !c[i].isWhitespace && c[i] != ">" { i += 1 }
                        value = String(c[start..<i])
                    }
                }
                if attributes[key] == nil { attributes[key] = decoded(value) }
            }
            guard complete else { break }
            if !suppressed.isEmpty {
                if closing && name == suppressed.last { suppressed.removeLast() }
                else if !closing && name == suppressed.last { suppressed.append(name) }
                continue
            }
            if discarded.contains(name) { if !closing && name != "embed" { suppressed.append(name) }; continue }
            if name == "img", !closing {
                let alt = escaped(attributes["alt"] ?? "Image")
                if input.allowsImages, images.count < 40,
                   let value = attributes["src"], let remote = ReaderURL.allowed(value, relativeTo: input.baseURL),
                   let local = URL(string: "rss-image://\(generation)/\(images.count)") {
                    images[local] = remote; output += "<img src=\"\(local.absoluteString)\" alt=\"\(alt)\">"
                } else { output += "<span> [\(alt)] </span>" }
                continue
            }
            guard formatting.contains(name) else { continue }
            if closing { output += "</\(name)>"; continue }
            if name == "a", let value = attributes["href"], let remote = ReaderURL.allowed(value, relativeTo: input.baseURL), let local = URL(string: "rss-link://\(generation)/\(links.count)") {
                links[local] = remote; output += "<a href=\"\(local.absoluteString)\">"
            } else { output += "<\(name)>" }
        }
        let nonce = UUID().uuidString
        let imagePolicy = input.allowsImages ? "rss-image:" : "'none'"
        let html = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'none'; style-src 'nonce-\(nonce)'; img-src \(imagePolicy); connect-src 'none'; frame-src 'none'; object-src 'none'; media-src 'none'; font-src 'none'; base-uri 'none'; form-action 'none';"><meta http-equiv="x-dns-prefetch-control" content="off"><style nonce="\(nonce)">
        :root{color-scheme:light dark}body{font:-apple-system-body;line-height:1.55;margin:8px 20px 48px;overflow-wrap:break-word}h1{font:-apple-system-title1;font-weight:bold}.meta{color:#8e8e93;margin-bottom:24px}img{max-width:100%;height:auto}a{color:#e0521a}blockquote{border-left:3px solid #888;padding-left:14px}pre,table{overflow-x:auto}code{font-family:ui-monospace,Menlo,monospace}
        </style></head><body><h1>\(escaped(input.title))</h1><p class="meta">\(escaped(input.metadata))</p>\(output)</body></html>
        """
        return ReaderDocument(html: html, images: images, links: links)
    }
}

nonisolated final class ReaderRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private var redirects = 0
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        redirects += 1
        guard redirects <= 3, let url = request.url, ReaderURL.allowed(url.absoluteString) != nil else { completionHandler(nil); return }
        var clean = URLRequest(url: url); clean.cachePolicy = .reloadIgnoringLocalCacheData
        completionHandler(clean)
    }
}

nonisolated enum ReaderImageFetcher {
    static func fetch(_ url: URL) async throws -> Data {
        guard ReaderURL.allowed(url.absoluteString) != nil else { throw URLError(.unsupportedURL) }
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil; config.httpCookieStorage = nil; config.urlCredentialStorage = nil
        config.httpShouldSetCookies = false; config.timeoutIntervalForRequest = 15; config.timeoutIntervalForResource = 20
        let session = URLSession(configuration: config)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url); request.cachePolicy = .reloadIgnoringLocalCacheData
        let (bytes, response) = try await session.bytes(for: request, delegate: ReaderRedirectDelegate())
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              ["image/png", "image/jpeg", "image/gif", "image/webp"].contains(http.mimeType?.lowercased() ?? ""),
              http.expectedContentLength <= 5_000_000 else { throw URLError(.cannotDecodeContentData) }
        var data = Data()
        for try await byte in bytes {
            try Task.checkCancellation()
            guard data.count < 5_000_000 else { throw URLError(.dataLengthExceedsMaximum) }
            data.append(byte)
        }
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0, width <= 8000, height <= 8000, width * height <= 16_000_000,
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceThumbnailMaxPixelSize: 1024,
                  kCGImageSourceCreateThumbnailWithTransform: true
              ] as CFDictionary) else { throw URLError(.cannotDecodeContentData) }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, "public.png" as CFString, 1, nil) else { throw URLError(.cannotDecodeContentData) }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else { throw URLError(.cannotDecodeContentData) }
        return output as Data
    }
}

@MainActor final class ReaderImageHandler: NSObject, WKURLSchemeHandler {
    private var urls: [URL: URL] = [:]
    private var tasks: [ObjectIdentifier: Task<Void, Never>] = [:]
    func replace(_ urls: [URL: URL]) {
        for task in tasks.values { task.cancel() }; tasks.removeAll(); self.urls = urls
    }
    func webView(_ webView: WKWebView, start urlSchemeTask: any WKURLSchemeTask) {
        let id = ObjectIdentifier(urlSchemeTask)
        guard let local = urlSchemeTask.request.url, let remote = urls[local] else {
            urlSchemeTask.didFailWithError(URLError(.unsupportedURL)); return
        }
        tasks[id] = Task { [weak self] in
            do {
                let data = try await ReaderImageFetcher.fetch(remote)
                guard let self, self.tasks[id] != nil, !Task.isCancelled else { return }
                self.tasks.removeValue(forKey: id)
                urlSchemeTask.didReceive(URLResponse(url: local, mimeType: "image/png", expectedContentLength: data.count, textEncodingName: nil))
                urlSchemeTask.didReceive(data); urlSchemeTask.didFinish()
            } catch {
                guard let self, self.tasks[id] != nil, !Task.isCancelled else { return }
                self.tasks.removeValue(forKey: id); urlSchemeTask.didFailWithError(error)
            }
        }
    }
    func webView(_ webView: WKWebView, stop urlSchemeTask: any WKURLSchemeTask) {
        tasks.removeValue(forKey: ObjectIdentifier(urlSchemeTask))?.cancel()
    }
}

@MainActor final class PrivateReaderSession: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    private let images = ReaderImageHandler()
    private var pending: ReaderInput?
    private var rulesReady = false
    private var expectedLocalNavigation = false
    private var compilation: Task<Void, Never>?
    private(set) var document: ReaderDocument?
    var didFinish: (() -> Void)?
    let openExternal: @MainActor (URL) -> Void
    init(openExternal: @escaping @MainActor (URL) -> Void = { UIApplication.shared.open($0, options: [:], completionHandler: nil) }) {
        self.openExternal = openExternal
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        config.defaultWebpagePreferences.allowsContentJavaScript = false
        config.setURLSchemeHandler(images, forURLScheme: "rss-image")
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        webView.navigationDelegate = self; webView.isOpaque = false
        webView.backgroundColor = .clear; webView.scrollView.backgroundColor = .clear
        compilation = Task { [weak self] in
            do {
                let rules = try await WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "RSSPrivateReader-v1", encodedContentRuleList: "[{\"trigger\":{\"url-filter\":\"^https?://\"},\"action\":{\"type\":\"block\"}}]")
                guard let self, !Task.isCancelled, let rules else { return }
                self.webView.configuration.userContentController.add(rules)
                self.rulesReady = true; self.render()
            } catch {
                guard let self, !Task.isCancelled else { return }
                self.expectedLocalNavigation = true
                self.webView.loadHTMLString("<p>Unable to open the private reader. Open the full article in your browser.</p>", baseURL: nil)
            }
        }
    }
    func load(_ input: ReaderInput) {
        guard pending != input else { return }
        pending = input; images.replace([:]); webView.stopLoading()
        if rulesReady { render() }
    }
    private func render() {
        guard let pending else { return }
        let doc = ReaderSanitizer.document(pending); document = doc
        images.replace(doc.images); expectedLocalNavigation = true
        webView.loadHTMLString(doc.html, baseURL: nil)
    }
    func cancel() {
        compilation?.cancel(); pending = nil; document = nil
        images.replace([:]); webView.stopLoading(); expectedLocalNavigation = false
    }
    func destination(for url: URL, explicitlyTapped: Bool) -> URL? {
        guard explicitlyTapped, let remote = document?.links[url] else { return nil }
        return ReaderURL.allowed(remote.absoluteString)
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard let url = navigationAction.request.url else { return .cancel }
        if expectedLocalNavigation, navigationAction.navigationType == .other,
           navigationAction.targetFrame?.isMainFrame == true, url.absoluteString == "about:blank" {
            expectedLocalNavigation = false; return .allow
        }
        if let remote = destination(for: url, explicitlyTapped: navigationAction.navigationType == .linkActivated) { openExternal(remote) }
        return .cancel
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { didFinish?() }
}

struct PrivateArticleWebView: UIViewRepresentable {
    var input: ReaderInput
    func makeCoordinator() -> PrivateReaderSession { PrivateReaderSession() }
    func makeUIView(context: Context) -> WKWebView { context.coordinator.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) { context.coordinator.load(input) }
    static func dismantleUIView(_ uiView: WKWebView, coordinator: PrivateReaderSession) { coordinator.cancel() }
}
