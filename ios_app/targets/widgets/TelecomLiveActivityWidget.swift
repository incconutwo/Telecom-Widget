import ActivityKit
import WidgetKit
import SwiftUI

// ─── Carrier Color & Identity Helper ──────────────────────────────────────────
struct CarrierTheme {
    let brandColor: Color
    let badgeBg: Color
    let shortName: String

    static func from(operatorName: String) -> CarrierTheme {
        let lower = operatorName.lowercased()
        if lower.contains("orange") || lower.contains("أورنج") || lower.contains("ⵓⵕⴰⵏⵊ") {
            return CarrierTheme(
                brandColor: Color(red: 255/255, green: 121/255, blue: 0/255),
                badgeBg: Color(red: 255/255, green: 121/255, blue: 0/255).opacity(0.22),
                shortName: "Orange"
            )
        } else if lower.contains("inwi") || lower.contains("إنوي") || lower.contains("ⵉⵏⵡⵉ") {
            return CarrierTheme(
                brandColor: Color(red: 214/255, green: 0/255, blue: 110/255),
                badgeBg: Color(red: 214/255, green: 0/255, blue: 110/255).opacity(0.22),
                shortName: "Inwi"
            )
        } else {
            return CarrierTheme(
                brandColor: Color(red: 10/255, green: 132/255, blue: 255/255),
                badgeBg: Color(red: 10/255, green: 132/255, blue: 255/255).opacity(0.22),
                shortName: "IAM"
            )
        }
    }
}

// ─── Native ActivityKit Live Activity & Dynamic Island Widget ─────────────────
public struct TelecomLiveActivityWidget: Widget {
    public init() {}

    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: TelecomWidgetAttributes.self) { context in
            // ─── Lock Screen / StandBy Banner UI ──────────────────────────────────────
            let state = context.state
            let theme = CarrierTheme.from(operatorName: state.operatorName)
            let cleanPhone = state.phoneNumber.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
            let deepLinkUrl = URL(string: "telecomwidget://account/\(cleanPhone)")

            VStack(alignment: .leading, spacing: 10) {
                // Header: Operator badge & Phone number
                HStack(alignment: .center) {
                    HStack(spacing: 6) {
                        Text(state.operatorName)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)

                        Text(theme.shortName)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(theme.brandColor)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(theme.badgeBg)
                            .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                    }

                    Spacer()

                    Text(state.phoneNumber)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.65))
                        .environment(\.layoutDirection, .leftToRight)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }

                // 2-Column Metric Grid (Internet & Calls)
                HStack(spacing: 8) {
                    // Left Tile: Internet
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 5) {
                            Image(systemName: "globe")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(theme.brandColor)
                                .frame(width: 20, height: 20)
                                .background(theme.badgeBg)
                                .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))

                            Text(state.internetLabel)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color.white.opacity(0.7))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)

                            Spacer(minLength: 2)

                            Text("\(Int(state.internetPercent))%")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color.white.opacity(0.6))
                                .monospacedDigit()
                        }

                        Text(state.internetRemaining)
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .allowsTightening(true)

                        // Progress Gauge
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.12))
                                    .frame(height: 4.5)
                                Capsule()
                                    .fill(theme.brandColor)
                                    .frame(
                                        width: max(4.0, min(geo.size.width, geo.size.width * CGFloat(state.internetPercent / 100.0))),
                                        height: 4.5
                                    )
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

                            Text(state.callsLabel)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color.white.opacity(0.7))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)

                            Spacer(minLength: 2)

                            Text("\(Int(state.callsPercent))%")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color.white.opacity(0.6))
                                .monospacedDigit()
                        }

                        Text(state.callsRemaining)
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.65)
                            .allowsTightening(true)

                        // Progress Gauge
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.12))
                                    .frame(height: 4.5)
                                Capsule()
                                    .fill(Color(red: 48/255, green: 209/255, blue: 88/255))
                                    .frame(
                                        width: max(4.0, min(geo.size.width, geo.size.width * CGFloat(state.callsPercent / 100.0))),
                                        height: 4.5
                                    )
                            }
                        }
                        .frame(height: 4.5)
                    }
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            .padding(14)
            .widgetURL(deepLinkUrl)
            .activityBackgroundTint(Color.black.opacity(0.85))
            .activitySystemActionForegroundColor(.white)

        } dynamicIsland: { context in
            let state = context.state
            let theme = CarrierTheme.from(operatorName: state.operatorName)
            let cleanPhone = state.phoneNumber.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
            let deepLinkUrl = URL(string: "telecomwidget://account/\(cleanPhone)")

            return DynamicIsland {
                // ─── Expanded Dynamic Island ──────────────────────────────────────────
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 4) {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(theme.brandColor)
                        Text(theme.shortName)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                    }
                    .padding(.leading, 3)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    Text(state.phoneNumber)
                        .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.75))
                        .environment(\.layoutDirection, .leftToRight)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .padding(.trailing, 3)
                }

                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 10) {
                        // Internet Column
                        HStack(spacing: 6) {
                            Image(systemName: "globe")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(theme.brandColor)
                                .frame(width: 20, height: 20)
                                .background(theme.badgeBg)
                                .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))

                            VStack(alignment: .leading, spacing: 1) {
                                Text(state.internetLabel)
                                    .font(.system(size: 9.5, weight: .medium))
                                    .foregroundColor(Color.white.opacity(0.55))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                                Text(state.internetRemaining)
                                    .font(.system(size: 13.5, weight: .bold, design: .rounded))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.75)
                                    .allowsTightening(true)
                            }
                        }

                        Spacer(minLength: 4)

                        // Calls Column
                        HStack(spacing: 6) {
                            Image(systemName: "phone.fill")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color(red: 48/255, green: 209/255, blue: 88/255))
                                .frame(width: 20, height: 20)
                                .background(Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20))
                                .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))

                            VStack(alignment: .leading, spacing: 1) {
                                Text(state.callsLabel)
                                    .font(.system(size: 9.5, weight: .medium))
                                    .foregroundColor(Color.white.opacity(0.55))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                                Text(state.callsRemaining)
                                    .font(.system(size: 13.5, weight: .bold, design: .rounded))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                                    .allowsTightening(true)
                            }
                        }
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 3)
                }
            } compactLeading: {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(theme.brandColor)
                    .frame(width: 18, height: 18)
                    .padding(.leading, 1)
            } compactTrailing: {
                Text(state.internetRemaining)
                    .font(.system(size: 11.5, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.65)
                    .allowsTightening(true)
                    .frame(maxWidth: 52, alignment: .trailing)
            } minimal: {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(theme.brandColor)
            }
            .widgetURL(deepLinkUrl)
        }
    }
}
