import Foundation

/// Diagnostic messages contain only a fixed operation and a numeric error code.
/// Never format the error description, domain, userInfo, underlying errors or URLs here.
nonisolated enum AppDiagnostic {
    enum Operation: String {
        case storageInitialization = "Storage initialization"
        case csvExport = "CSV export"
    }
    static func message(_ operation: Operation, error: Error) -> String {
        "\(operation.rawValue) failed (error code \((error as NSError).code))."
    }
}
