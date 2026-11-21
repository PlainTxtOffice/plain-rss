import SwiftUI

/// Local privacy information remains readable without a network connection.
struct PrivacyPolicyView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack { policyContent }
    }

    private var policyContent: some View {
        List {
            Section {
                Text("Plain RSS").font(.headline)
                Text("Updated November 20, 2026").font(.footnote).foregroundStyle(.secondary)
            }
            Section("On your device") {
                Text("Feed URLs and titles, downloaded articles, read and starred status, and refresh metadata are stored locally. OPML import and export read or write the subscription files you choose.").textSelection(.enabled)
            }
            Section("Feed servers and article resources") {
                Text("Adding or refreshing a feed sends requests to its server, which receives the requested URL and connection information such as your IP address. The in-app reader displays text and basic formatting with remote content blocked by default. If you choose Load images for an article, image servers and their redirects may receive your IP address and image URLs. This temporary image permission resets when you leave the article, switch articles, hide images or move the app out of the foreground. Scripts, frames, publisher styles, fonts and media remain blocked. Images use a temporary downloader without saved cookies, credentials or cache; the reader uses nonpersistent website storage.").textSelection(.enabled)
            }
            Section("Links and sharing") {
                Text("Opening an article or tapped link uses the destination service. Exporting OPML creates a copy of your feed list at the destination you choose. Feed providers, websites and file providers apply their own policies.").textSelection(.enabled)
            }
            Section("Removing data") {
                Text("Deleting a feed removes its saved articles, including read and starred status. It does not erase information already held by publishers, exported OPML files or data in your external browser. The in-app reader does not retain persistent website cookies or cache. Deleting the app removes local app storage; backups and external copies are separate.").textSelection(.enabled)
            }
            Section("Developer services") {
                Text("This version contains no developer-run analytics, advertising or account service. Device backups and services you choose, such as sharing destinations or file providers, follow their own settings and policies.").textSelection(.enabled)
            }
            Section("Apple diagnostics") {
                Text("We intend to use Apple-provided crash and performance information, when available under your Apple diagnostic-sharing choices, only to troubleshoot problems and improve reliability. App diagnostic logging should not intentionally include document text, task contents, feed bodies, private filenames, URLs, tokens or other private content. Apple controls its system-generated reports; we cannot guarantee those reports contain no sensitive information.").textSelection(.enabled)
            }
            Section("Feedback") {
                Text("Send Feedback opens a draft in your email app or the contact form in your browser. The email draft includes the app version, iOS version and device model. Your feeds and articles are not included. Nothing is sent unless you send it.").textSelection(.enabled)
            }
            Section("Questions") {
                Text("Contact opens our website. We use the name, email address and message you provide to respond to your request. Service providers may process this correspondence for us. We retain it as needed to resolve the request or meet legal obligations. Use the same contact form to request deletion.")
                Link("Contact Plain Text Office", destination: URL(string: "https://plaintxtoffice.com/contact/")!)
                    .accessibilityIdentifier("privacyContactLink")
            }
            Section("On the web") {
                Text("This information is also published on our website, with a link to the policy that applies to every Plain Text Office app.")
                Link("Privacy on plaintxtoffice.com", destination: URL(string: "https://plaintxtoffice.com/apps/plain-rss/#privacy")!)
                    .accessibilityIdentifier("privacyWebLink")
            }
        }
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityIdentifier("privacyPolicyContent")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
                    .accessibilityIdentifier("privacyPolicyDone")
            }
        }
    }
}
