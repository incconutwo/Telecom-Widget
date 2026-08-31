import ActivityKit
import WidgetKit
import SwiftUI

public struct TelecomWidgetAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var operatorName: String
        public var phoneNumber: String
        public var internetRemaining: String
        public var internetPercent: Double
        public var internetLabel: String
        public var callsRemaining: String
        public var callsPercent: Double
        public var callsLabel: String
        public var timestamp: Double

        public init(
            operatorName: String = "Maroc Telecom",
            phoneNumber: String = "",
            internetRemaining: String = "0 Go",
            internetPercent: Double = 0.0,
            internetLabel: String = "Internet",
            callsRemaining: String = "0h 00m",
            callsPercent: Double = 0.0,
            callsLabel: String = "Calls",
            timestamp: Double = Date().timeIntervalSince1970
        ) {
            self.operatorName = operatorName
            self.phoneNumber = phoneNumber
            self.internetRemaining = internetRemaining
            self.internetPercent = internetPercent
            self.internetLabel = internetLabel
            self.callsRemaining = callsRemaining
            self.callsPercent = callsPercent
            self.callsLabel = callsLabel
            self.timestamp = timestamp
        }
    }

    public var accountId: String

    public init(accountId: String) {
        self.accountId = accountId
    }
}
