//
//  StorageRecovery.swift
//

import SwiftUI
import SwiftData

/// Opens the on-disk SwiftData store. If that fails, the store on disk is left
/// untouched and the app runs on a temporary in-memory container instead, so
/// it can still launch. Nothing entered while in that state is saved.
@MainActor @Observable
final class StorageRecovery {
    private(set) var container: ModelContainer
    /// Non-nil while running on the temporary in-memory fallback.
    private(set) var failureMessage: String?
    /// The user chose to continue without saving.
    var continuedWithoutSaving = false

    private let schema: Schema
    private let openStore: () throws -> ModelContainer

    init(schema: Schema, openStore: (() throws -> ModelContainer)? = nil) {
        self.schema = schema
        self.openStore = openStore ?? {
            try ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)])
        }
        do {
            container = try self.openStore()
        } catch {
            container = Self.makeFallback(schema: schema)
            failureMessage = Self.describe(error)
        }
    }

    /// Tries the on-disk store again. On failure the fallback stays in place.
    func retry() {
        do {
            container = try openStore()
            failureMessage = nil
            continuedWithoutSaving = false
        } catch {
            failureMessage = Self.describe(error)
        }
    }

    private static func describe(_ error: Error) -> String {
        AppDiagnostic.message(.storageInitialization, error: error)
    }

    private static func makeFallback(schema: Schema) -> ModelContainer {
        do {
            return try ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)])
        } catch {
            // An in-memory store failing means the schema itself is invalid; there is nothing left to run on.
            fatalError(describe(error))
        }
    }
}

/// Shows `content` on the recovered container. While the on-disk store is
/// unavailable, blocks with an explanation until the user retries or chooses
/// to continue without saving, then keeps a warning banner visible.
struct StorageRecoveryGate<Content: View>: View {
    let recovery: StorageRecovery
    @ViewBuilder var content: () -> Content

    var body: some View {
        if recovery.failureMessage != nil && !recovery.continuedWithoutSaving {
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(.orange)
                Text("Your data couldn't be opened")
                    .font(.title2.bold())
                Text("Your saved data is still on this device and hasn't been changed. If you continue now, anything you add or change won't be saved.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                if let message = recovery.failureMessage {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Button("Retry") { recovery.retry() }
                    .buttonStyle(.borderedProminent)
                Button("Continue Without Saving") { recovery.continuedWithoutSaving = true }
            }
            .padding()
        } else {
            content()
                .modelContainer(recovery.container)
                .id(ObjectIdentifier(recovery.container))
                .safeAreaInset(edge: .top, spacing: 0) {
                    if recovery.failureMessage != nil {
                        Text("Your data couldn't be opened. Changes won't be saved.")
                            .font(.footnote.bold())
                            .frame(maxWidth: .infinity)
                            .padding(8)
                            .background(Color.orange)
                            .foregroundStyle(.black)
                    }
                }
        }
    }
}
