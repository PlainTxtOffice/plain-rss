import SwiftUI
import UIKit

/// Kind of feedback a drafted email is about.
nonisolated enum FeedbackKind {
    case bugReport
    case featureRequest

    var label: String {
        switch self {
        case .bugReport: return String(localized: "bug report")
        case .featureRequest: return String(localized: "feature request")
        }
    }
}

/// Builds the feedback email draft. The draft carries only the subject and the device
/// summary; feed addresses, article content and the library are never added.
nonisolated enum Feedback {
    /// Address that receives bug reports and feature requests.
    static let address = "feedback@plaintxtoffice.com"
    /// Contact page that receives bug reports, feature requests, and privacy questions.
    static let contactURL = URL(string: "https://plaintxtoffice.com/contact/")!

    /// Three lines: app version and build, system name and version, then device model.
    static func deviceSummary(
        appName: String,
        version: String,
        build: String,
        systemName: String,
        systemVersion: String,
        model: String
    ) -> String {
        "\(appName) \(version) (\(build))\n\(systemName) \(systemVersion)\n\(model)"
    }

    /// Draft body: blank lines for the user's message, ending with the device summary.
    static func emailBody(deviceSummary: String) -> String {
        "\n\n" + deviceSummary
    }

    static func subject(for kind: FeedbackKind) -> String {
        "Plain RSS \(kind.label) (iOS)"
    }

    /// A `mailto:` address that opens a draft to `address` in the user's email app.
    static func emailURL(kind: FeedbackKind, deviceSummary: String) -> URL? {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = address
        components.queryItems = [
            URLQueryItem(name: "subject", value: subject(for: kind)),
            URLQueryItem(name: "body", value: emailBody(deviceSummary: deviceSummary)),
        ]
        return components.url
    }

    /// Hardware model identifier, such as `iPhone17,1`. Names the device model, not the device.
    static func modelIdentifier() -> String {
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: &system.machine) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
    }
}

extension Feedback {
    /// App and device facts that help reproduce a report. Never includes feeds or articles.
    @MainActor static func currentDeviceSummary() -> String {
        let info = Bundle.main.infoDictionary
        return deviceSummary(
            appName: "Plain RSS",
            version: info?["CFBundleShortVersionString"] as? String ?? "",
            build: info?["CFBundleVersion"] as? String ?? "",
            systemName: UIDevice.current.systemName,
            systemVersion: UIDevice.current.systemVersion,
            model: modelIdentifier()
        )
    }
}

/// Lets the user draft a bug report or feature request email, or open the contact form.
struct FeedbackView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Found a problem or have an idea? Report a bug and Request a feature open a draft in your email app, addressed to Plain Text Office, with your app version, iOS version and device model filled in. Open contact form opens the contact form in your browser. Nothing is sent unless you send it.")
                    Text("For a bug, include what you did, what happened, and the feed address if one is involved.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section {
                    Button("Report a bug", systemImage: "ladybug") { sendEmail(.bugReport) }
                        .accessibilityIdentifier("feedbackReportBugButton")
                    Button("Request a feature", systemImage: "lightbulb") { sendEmail(.featureRequest) }
                        .accessibilityIdentifier("feedbackRequestFeatureButton")
                    Button("Open contact form", systemImage: "safari") {
                        open(Feedback.contactURL, failure: "The contact form couldn’t be opened. Use Report a bug or Request a feature to write an email instead.")
                    }
                    .accessibilityIdentifier("feedbackContactFormButton")
                } footer: {
                    if let errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("feedbackError")
                    }
                }
            }
            .navigationTitle("Send Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .accessibilityIdentifier("feedbackDone")
                }
            }
        }
    }

    private func sendEmail(_ kind: FeedbackKind) {
        let failure: LocalizedStringResource = "No email app is set up on this device. Use Open contact form instead."
        guard let url = Feedback.emailURL(kind: kind, deviceSummary: Feedback.currentDeviceSummary()) else {
            errorMessage = String(localized: failure)
            return
        }
        open(url, failure: failure)
    }

    private func open(_ url: URL, failure: LocalizedStringResource) {
        openURL(url) { accepted in
            errorMessage = accepted ? nil : String(localized: failure)
        }
    }
}

#Preview {
    FeedbackView()
}
