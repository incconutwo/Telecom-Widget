import AppIntents
import WidgetKit
import SwiftUI

// ─── Telecom Account App Entity for Native iOS "Edit Widget" ──────────────────
public struct TelecomAccountEntity: AppEntity {
    public static var typeDisplayRepresentation: TypeDisplayRepresentation = "Telecom Account"
    public static var defaultQuery = TelecomAccountQuery()

    public var id: String
    public var name: String
    public var operatorName: String
    public var phoneNumber: String

    public var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            title: "\(name)",
            subtitle: "\(operatorName)"
        )
    }

    public init(id: String, name: String, operatorName: String, phoneNumber: String) {
        self.id = id
        self.name = name
        self.operatorName = operatorName
        self.phoneNumber = phoneNumber
    }
}

// ─── Query to dynamically fetch connected SIM accounts from App Group ────────
public struct TelecomAccountQuery: EntityQuery {
    public init() {}

    public func entities(for identifiers: [String]) async throws -> [TelecomAccountEntity] {
        let all = fetchAllAccounts()
        return all.filter { identifiers.contains($0.id) }
    }

    public func suggestedEntities() async throws -> [TelecomAccountEntity] {
        return fetchAllAccounts()
    }

    // Helper to dynamically find the App Group from the signed provisioning profile (Crucial for AltStore)
    private func getAppGroup() -> String {
        // Fallback default
        let defaultGroup = "group.com.telecom.widget"
        guard let provisionPath = Bundle.main.path(forResource: "embedded", ofType: "mobileprovision"),
              let provisionString = try? String(contentsOfFile: provisionPath, encoding: .isoLatin1) else { return defaultGroup }
        guard let startRange = provisionString.range(of: "<?xml"),
              let endRange = provisionString.range(of: "</plist>") else { return defaultGroup }
        let xmlString = String(provisionString[startRange.lowerBound..<endRange.upperBound])
        guard let data = xmlString.data(using: .utf8),
              let plist = try? PropertyListSerialization.propertyList(from: data, options: [], format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any],
              let appGroups = entitlements["com.apple.security.application-groups"] as? [String],
              let firstGroup = appGroups.first else { return defaultGroup }
        return firstGroup
    }

    public func defaultResult() async -> TelecomAccountEntity? {
        let all = fetchAllAccounts()
        let appGroupName = getAppGroup()
        let userDefaults = UserDefaults(suiteName: appGroupName)
        var activeId = userDefaults?.string(forKey: "active_account_id")
        if activeId == nil || activeId?.isEmpty == true {
            if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupName) {
                let fileURL = container.appendingPathComponent("active_account_id.txt")
                activeId = try? String(contentsOf: fileURL, encoding: .utf8)
            }
        }
        return all.first(where: { $0.id == activeId }) ?? all.first
    }

    private func fetchAllAccounts() -> [TelecomAccountEntity] {
        var jsonString: String? = nil
        let appGroupName = getAppGroup()
        let userDefaults = UserDefaults(suiteName: appGroupName)
        jsonString = userDefaults?.string(forKey: "all_accounts_data")

        if jsonString == nil || jsonString?.isEmpty == true {
            if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupName) {
                let fileURL = container.appendingPathComponent("all_accounts.json")
                jsonString = try? String(contentsOf: fileURL, encoding: .utf8)
            }
        }

        guard let validJson = jsonString,
              let data = validJson.data(using: .utf8) else {
            return []
        }

        struct RawAccount: Codable {
            let id: String
            let operatorName: String
            let phone: String
        }

        do {
            let list = try JSONDecoder().decode([RawAccount].self, from: data)
            return list.map { acc in
                let displayName = acc.phone.isEmpty ? acc.operatorName : "\(acc.operatorName) • \(acc.phone)"
                return TelecomAccountEntity(
                    id: acc.id,
                    name: displayName,
                    operatorName: acc.operatorName,
                    phoneNumber: acc.phone
                )
            }
        } catch {
            return []
        }
    }
}

// ─── Widget Configuration Intent (Shown when user taps "Edit Widget") ─────────
public struct SelectAccountIntent: WidgetConfigurationIntent {
    public static var title: LocalizedStringResource = "Select Account"
    public static var description = IntentDescription("Choose which telecom account to display on this widget.")

    @Parameter(title: "Account")
    public var account: TelecomAccountEntity?

    @Parameter(title: "Show Balance Details", default: true)
    public var showBalance: Bool

    public init() {}

    public init(account: TelecomAccountEntity?, showBalance: Bool = true) {
        self.account = account
        self.showBalance = showBalance
    }
}

// ─── Interactive Widget Refresh Intent (iOS 17+ Button Action) ────────────────
public struct RefreshWidgetIntent: AppIntent {
    public static var title: LocalizedStringResource = "Refresh Telecom Balance"
    public static var description = IntentDescription("Refreshes widget balance numbers from latest data.")

    public init() {}

    public func perform() async throws -> some IntentResult {
        if #available(iOS 14.0, *) {
            WidgetCenter.shared.reloadAllTimelines()
        }
        return .result()
    }
}
