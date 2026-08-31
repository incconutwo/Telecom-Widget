import WidgetKit
import SwiftUI
import AppIntents

// ─── Backward Compatibility Extension for iOS 16 & 17+ Backgrounds ──────────
extension View {
    @ViewBuilder
    func applyWidgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            self.containerBackground(for: .widget) {
                color
            }
        } else {
            self.background(color)
        }
    }
}

// ─── Timeline Entry ───────────────────────────────────────────────────────────
public struct TelecomWidgetEntry: TimelineEntry {
    public let date: Date
    public let operatorName: String
    public let phoneNumber: String
    public let internetRemaining: String
    public let internetPercent: Double
    public let internetLabel: String
    public let callsRemaining: String
    public let callsPercent: Double
    public let callsLabel: String
    public let mainBalance: String
    public let showBalance: Bool
    public let timestamp: Double
}

// ─── AppIntent Timeline Provider for Dynamic Account Selection ───────────────
public struct TelecomAppIntentProvider: AppIntentTimelineProvider {
    public typealias Entry = TelecomWidgetEntry
    public typealias Intent = SelectAccountIntent

    public func placeholder(in context: Context) -> TelecomWidgetEntry {
        TelecomWidgetEntry(
            date: Date(),
            operatorName: "Maroc Telecom",
            phoneNumber: "+212 6 10 65 36 94",
            internetRemaining: "10,04 Go",
            internetPercent: 65.0,
            internetLabel: "Internet",
            callsRemaining: "02h 25m",
            callsPercent: 45.0,
            callsLabel: "Calls",
            mainBalance: "25.00 DH",
            showBalance: true,
            timestamp: Date().timeIntervalSince1970
        )
    }

    public func snapshot(for configuration: SelectAccountIntent, in context: Context) async -> TelecomWidgetEntry {
        return readAccountData(for: configuration)
    }

    public func timeline(for configuration: SelectAccountIntent, in context: Context) async -> Timeline<TelecomWidgetEntry> {
        let entry = readAccountData(for: configuration)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        return Timeline(entries: [entry], policy: .after(nextUpdate))
    }

    private func readAccountData(for configuration: SelectAccountIntent) -> TelecomWidgetEntry {
        let userDefaults = UserDefaults(suiteName: "group.com.telecom.widget")
        var activeId = userDefaults?.string(forKey: "active_account_id")
        var jsonString = userDefaults?.string(forKey: "all_accounts_data")

        let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.telecom.widget")

        if jsonString == nil || jsonString?.isEmpty == true {
            if let container = containerURL {
                let fileURL = container.appendingPathComponent("all_accounts.json")
                jsonString = try? String(contentsOf: fileURL, encoding: .utf8)
            }
        }

        if activeId == nil || activeId?.isEmpty == true {
            if let container = containerURL {
                let fileURL = container.appendingPathComponent("active_account_id.txt")
                activeId = try? String(contentsOf: fileURL, encoding: .utf8)
            }
        }

        let targetAccountId = configuration.account?.id ?? activeId
        let showBalance = configuration.showBalance

        struct AccountData: Codable {
            let id: String
            let operatorName: String
            let phone: String
            let internetRemaining: String
            let internetPercent: Double
            let internetLabel: String
            let callsRemaining: String
            let callsPercent: Double
            let callsLabel: String
            let mainBalance: String
            let timestamp: Double
        }

        if let validJson = jsonString,
           let data = validJson.data(using: .utf8),
           let list = try? JSONDecoder().decode([AccountData].self, from: data),
           let matched = list.first(where: { $0.id == targetAccountId }) ?? list.first {
            return TelecomWidgetEntry(
                date: Date(),
                operatorName: matched.operatorName,
                phoneNumber: matched.phone,
                internetRemaining: matched.internetRemaining,
                internetPercent: matched.internetPercent,
                internetLabel: matched.internetLabel,
                callsRemaining: matched.callsRemaining,
                callsPercent: matched.callsPercent,
                callsLabel: matched.callsLabel,
                mainBalance: matched.mainBalance,
                showBalance: showBalance,
                timestamp: matched.timestamp
            )
        }

        // Check fallback JSON file
        if let container = containerURL {
            let widgetDataFile = container.appendingPathComponent("widget_data.json")
            if let data = try? Data(contentsOf: widgetDataFile),
               let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let op = dict["operator"] as? String ?? "Maroc Telecom"
                let phone = dict["phoneNumber"] as? String ?? ""
                let internet = dict["internetRemaining"] as? String ?? "0 Go"
                let internetPercent = dict["internetPercent"] as? Double ?? 0.0
                let internetLabel = dict["internetLabel"] as? String ?? "Internet"
                let calls = dict["callsRemaining"] as? String ?? "0h 00m"
                let callsPercent = dict["callsPercent"] as? Double ?? 0.0
                let callsLabel = dict["callsLabel"] as? String ?? "Calls"
                let mainBalance = dict["mainBalance"] as? String ?? ""
                let timestamp = dict["timestamp"] as? Double ?? Date().timeIntervalSince1970

                return TelecomWidgetEntry(
                    date: Date(),
                    operatorName: op,
                    phoneNumber: phone,
                    internetRemaining: internet,
                    internetPercent: internetPercent,
                    internetLabel: internetLabel,
                    callsRemaining: calls,
                    callsPercent: callsPercent,
                    callsLabel: callsLabel,
                    mainBalance: mainBalance,
                    showBalance: showBalance,
                    timestamp: timestamp
                )
            }
        }

        // Fallback to active single account fields in UserDefaults
        let op = userDefaults?.string(forKey: "widget_operator") ?? "Maroc Telecom"
        let phone = userDefaults?.string(forKey: "widget_phone") ?? ""
        let internet = userDefaults?.string(forKey: "widget_internet") ?? "0 Go"
        let internetPercent = userDefaults?.double(forKey: "widget_internet_percent") ?? 0.0
        let internetLabel = userDefaults?.string(forKey: "widget_internet_label") ?? "Internet"
        let calls = userDefaults?.string(forKey: "widget_calls") ?? "0h 00m"
        let callsPercent = userDefaults?.double(forKey: "widget_calls_percent") ?? 0.0
        let callsLabel = userDefaults?.string(forKey: "widget_calls_label") ?? "Calls"
        let mainBalance = userDefaults?.string(forKey: "widget_main_balance") ?? ""
        let timestamp = userDefaults?.double(forKey: "widget_timestamp") ?? Date().timeIntervalSince1970

        return TelecomWidgetEntry(
            date: Date(),
            operatorName: op,
            phoneNumber: phone,
            internetRemaining: internet,
            internetPercent: internetPercent,
            internetLabel: internetLabel,
            callsRemaining: calls,
            callsPercent: callsPercent,
            callsLabel: callsLabel,
            mainBalance: mainBalance,
            showBalance: showBalance,
            timestamp: timestamp
        )
    }
}

// ─── Small Widget View (systemSmall) ──────────────────────────────────────────
struct TelecomSmallWidgetView: View {
    let entry: TelecomWidgetEntry
    let theme: CarrierTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header
            HStack {
                Text(theme.shortName)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(theme.brandColor)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(theme.badgeBg)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))

                Spacer()

                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(theme.brandColor)
            }

            Spacer(minLength: 0)

            // Internet Section
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(entry.internetLabel)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.6))
                    Spacer()
                    Text("\(Int(entry.internetPercent))%")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(Color.white.opacity(0.5))
                        .monospacedDigit()
                }

                Text(entry.internetRemaining)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)

                // Progress Bar
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.white.opacity(0.12))
                            .frame(height: 3.5)
                        Capsule()
                            .fill(theme.brandColor)
                            .frame(width: max(3.0, min(geo.size.width, geo.size.width * CGFloat(entry.internetPercent / 100.0))), height: 3.5)
                    }
                }
                .frame(height: 3.5)
            }

            Spacer(minLength: 0)

            // Calls Section
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 8))
                        .foregroundColor(Color(red: 48/255, green: 209/255, blue: 88/255))
                    Text(entry.callsLabel)
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.6))
                    Spacer()
                    Text(entry.callsRemaining)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                }
            }
        }
        .padding(12)
    }
}

// ─── Medium Widget View (systemMedium) ────────────────────────────────────────
struct TelecomMediumWidgetView: View {
    let entry: TelecomWidgetEntry
    let theme: CarrierTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack(alignment: .center) {
                HStack(spacing: 6) {
                    Text(entry.operatorName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Text(theme.shortName)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(theme.brandColor)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(theme.badgeBg)
                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                }

                Spacer()

                HStack(spacing: 6) {
                    Text(entry.phoneNumber)
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.65))
                        .environment(\.layoutDirection, .leftToRight)
                        .lineLimit(1)

                    // Interactive Quick-Refresh Button
                    if #available(iOS 17.0, *) {
                        Button(intent: RefreshWidgetIntent()) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(Color.white.opacity(0.75))
                                .frame(width: 20, height: 20)
                                .background(Color.white.opacity(0.12))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            // 2-Column Side-by-Side Metric Grid
            HStack(spacing: 8) {
                // Left Tile: Internet
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 5) {
                        Image(systemName: "globe")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(theme.brandColor)
                            .frame(width: 18, height: 18)
                            .background(theme.badgeBg)
                            .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))

                        Text(entry.internetLabel)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.7))
                            .lineLimit(1)

                        Spacer(minLength: 2)

                        Text("\(Int(entry.internetPercent))%")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(Color.white.opacity(0.6))
                            .monospacedDigit()
                    }

                    Text(entry.internetRemaining)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)

                    // Progress Gauge
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.white.opacity(0.12))
                                .frame(height: 4)
                            Capsule()
                                .fill(theme.brandColor)
                                .frame(width: max(4.0, min(geo.size.width, geo.size.width * CGFloat(entry.internetPercent / 100.0))), height: 4)
                        }
                    }
                    .frame(height: 4)
                }
                .padding(8)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                // Right Tile: Calls
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 5) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(Color(red: 48/255, green: 209/255, blue: 88/255))
                            .frame(width: 18, height: 18)
                            .background(Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20))
                            .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))

                        Text(entry.callsLabel)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.7))
                            .lineLimit(1)

                        Spacer(minLength: 2)

                        Text("\(Int(entry.callsPercent))%")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(Color.white.opacity(0.6))
                            .monospacedDigit()
                    }

                    Text(entry.callsRemaining)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)

                    // Progress Gauge
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.white.opacity(0.12))
                                .frame(height: 4)
                            Capsule()
                                .fill(Color(red: 48/255, green: 209/255, blue: 88/255))
                                .frame(width: max(4.0, min(geo.size.width, geo.size.width * CGFloat(entry.callsPercent / 100.0))), height: 4)
                        }
                    }
                    .frame(height: 4)
                }
                .padding(8)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
        .padding(12)
    }
}

// ─── Large Widget View (systemLarge) ──────────────────────────────────────────
struct TelecomLargeWidgetView: View {
    let entry: TelecomWidgetEntry
    let theme: CarrierTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack(alignment: .center) {
                HStack(spacing: 6) {
                    Text(entry.operatorName)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Text(theme.shortName)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(theme.brandColor)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(theme.badgeBg)
                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                }

                Spacer()

                HStack(spacing: 6) {
                    Text(entry.phoneNumber)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.7))
                        .environment(\.layoutDirection, .leftToRight)
                        .lineLimit(1)

                    // Interactive Quick-Refresh Button
                    if #available(iOS 17.0, *) {
                        Button(intent: RefreshWidgetIntent()) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color.white.opacity(0.75))
                                .frame(width: 22, height: 22)
                                .background(Color.white.opacity(0.12))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            // 2-Column Side-by-Side Metric Grid (Internet & Calls)
            HStack(spacing: 10) {
                // Left Tile: Internet
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 5) {
                        Image(systemName: "globe")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(theme.brandColor)
                            .frame(width: 20, height: 20)
                            .background(theme.badgeBg)
                            .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))

                        Text(entry.internetLabel)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.7))

                        Spacer()

                        Text("\(Int(entry.internetPercent))%")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Color.white.opacity(0.6))
                            .monospacedDigit()
                    }

                    Text(entry.internetRemaining)
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.white.opacity(0.12))
                                .frame(height: 4.5)
                            Capsule()
                                .fill(theme.brandColor)
                                .frame(width: max(4.0, min(geo.size.width, geo.size.width * CGFloat(entry.internetPercent / 100.0))), height: 4.5)
                        }
                    }
                    .frame(height: 4.5)
                }
                .padding(10)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                // Right Tile: Calls
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 5) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Color(red: 48/255, green: 209/255, blue: 88/255))
                            .frame(width: 20, height: 20)
                            .background(Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20))
                            .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))

                        Text(entry.callsLabel)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.7))

                        Spacer()

                        Text("\(Int(entry.callsPercent))%")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Color.white.opacity(0.6))
                            .monospacedDigit()
                    }

                    Text(entry.callsRemaining)
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.white.opacity(0.12))
                                .frame(height: 4.5)
                            Capsule()
                                .fill(Color(red: 48/255, green: 209/255, blue: 88/255))
                            .frame(width: max(4.0, min(geo.size.width, geo.size.width * CGFloat(entry.callsPercent / 100.0))), height: 4.5)
                        }
                    }
                    .frame(height: 4.5)
                }
                .padding(10)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            // Middle Section: Balance / Solde Principal (if enabled by intent parameter)
            if entry.showBalance && !entry.mainBalance.isEmpty {
                HStack(spacing: 8) {
                    Image(systemName: "creditcard.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(theme.brandColor)
                    Text("Solde Principal")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.7))
                    Spacer()
                    Text(entry.mainBalance)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                }
                .padding(10)
                .background(Color.white.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            Spacer(minLength: 0)

            // Footer Section
            HStack {
                Text("Mis à jour récemment")
                    .font(.system(size: 10, weight: .regular))
                    .foregroundColor(Color.white.opacity(0.45))
                Spacer()
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(theme.brandColor)
            }
        }
        .padding(14)
    }
}

// ─── Entry View Switcher ──────────────────────────────────────────────────────
struct TelecomHomeScreenWidgetEntryView: View {
    var entry: TelecomAppIntentProvider.Entry
    @Environment(\.widgetFamily) var family

    var body: some View {
        let theme = CarrierTheme.from(operatorName: entry.operatorName)
        let cleanPhone = entry.phoneNumber.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
        let deepLinkUrl = URL(string: "telecomwidget://account/\(cleanPhone)")

        Group {
            switch family {
            case .systemSmall:
                TelecomSmallWidgetView(entry: entry, theme: theme)
            case .systemLarge:
                TelecomLargeWidgetView(entry: entry, theme: theme)
            default:
                TelecomMediumWidgetView(entry: entry, theme: theme)
            }
        }
        .widgetURL(deepLinkUrl)
        .applyWidgetBackground(Color.black.opacity(0.88))
    }
}

// ─── AppIntent Configurable Widget ────────────────────────────────────────────
public struct TelecomHomeScreenWidget: Widget {
    public let kind: String = "TelecomHomeScreenWidget"

    public init() {}

    public var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: SelectAccountIntent.self, provider: TelecomAppIntentProvider()) { entry in
            TelecomHomeScreenWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Telecom Balance")
        .description("Track your mobile internet, calling minutes, and balance.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
