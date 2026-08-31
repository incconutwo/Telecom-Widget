import Foundation
import ActivityKit
import WidgetKit
import React

@objc(TelecomActivityModule)
public class TelecomActivityModule: NSObject {

    @objc
    public static func requiresMainQueueSetup() -> Bool {
        return false
    }

    private func saveToUserDefaults(options: NSDictionary) {
        if let userDefaults = UserDefaults(suiteName: "group.com.telecom.widget") {
            let operatorName = options["operator"] as? String ?? "Maroc Telecom"
            let phoneNumber = options["phoneNumber"] as? String ?? ""
            let internetRemaining = options["internetRemaining"] as? String ?? "0 Go"
            let internetPercent = options["internetPercent"] as? Double ?? 0.0
            let internetLabel = options["internetLabel"] as? String ?? "Internet"
            let callsRemaining = options["callsRemaining"] as? String ?? "0h 00m"
            let callsPercent = options["callsPercent"] as? Double ?? 0.0
            let callsLabel = options["callsLabel"] as? String ?? "Calls"
            let mainBalance = options["mainBalance"] as? String ?? ""
            let timestamp = options["timestamp"] as? Double ?? Date().timeIntervalSince1970

            userDefaults.set(operatorName, forKey: "widget_operator")
            userDefaults.set(phoneNumber, forKey: "widget_phone")
            userDefaults.set(internetRemaining, forKey: "widget_internet")
            userDefaults.set(internetPercent, forKey: "widget_internet_percent")
            userDefaults.set(internetLabel, forKey: "widget_internet_label")
            userDefaults.set(callsRemaining, forKey: "widget_calls")
            userDefaults.set(callsPercent, forKey: "widget_calls_percent")
            userDefaults.set(callsLabel, forKey: "widget_calls_label")
            userDefaults.set(mainBalance, forKey: "widget_main_balance")
            userDefaults.set(timestamp, forKey: "widget_timestamp")
            userDefaults.synchronize()

            if #available(iOS 14.0, *) {
                WidgetCenter.shared.reloadAllTimelines()
            }
        }
    }

    @objc
    public func updateWidgetData(_ options: NSDictionary, resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        saveToUserDefaults(options: options)
        resolver(["status": "success"])
    }

    @objc
    public func syncAllAccounts(_ options: NSDictionary, resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        if let userDefaults = UserDefaults(suiteName: "group.com.telecom.widget") {
            let activeAccountId = options["activeAccountId"] as? String ?? ""
            let accountsJson = options["accountsJson"] as? String ?? "[]"

            userDefaults.set(activeAccountId, forKey: "active_account_id")
            userDefaults.set(accountsJson, forKey: "all_accounts_data")
            userDefaults.synchronize()

            if #available(iOS 14.0, *) {
                WidgetCenter.shared.reloadAllTimelines()
            }
            resolver(["status": "synced"])
        } else {
            rejecter("APP_GROUP_ERROR", "Could not access App Group suite", nil)
        }
    }

    @objc
    public func startOrUpdateActivity(_ options: NSDictionary, resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        saveToUserDefaults(options: options)

        guard #available(iOS 16.1, *) else {
            rejecter("UNSUPPORTED_OS", "Live Activities require iOS 16.1 or later", nil)
            return
        }

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            rejecter("DISABLED", "Live Activities are disabled in system settings", nil)
            return
        }

        let accountId = options["accountId"] as? String ?? "default"
        let operatorName = options["operator"] as? String ?? "Maroc Telecom"
        let phoneNumber = options["phoneNumber"] as? String ?? ""
        let internetRemaining = options["internetRemaining"] as? String ?? "0 Go"
        let internetPercent = options["internetPercent"] as? Double ?? 0.0
        let internetLabel = options["internetLabel"] as? String ?? "Internet"
        let callsRemaining = options["callsRemaining"] as? String ?? "0h 00m"
        let callsPercent = options["callsPercent"] as? Double ?? 0.0
        let callsLabel = options["callsLabel"] as? String ?? "Calls"
        let timestamp = options["timestamp"] as? Double ?? Date().timeIntervalSince1970

        let contentState = TelecomWidgetAttributes.ContentState(
            operatorName: operatorName,
            phoneNumber: phoneNumber,
            internetRemaining: internetRemaining,
            internetPercent: internetPercent,
            internetLabel: internetLabel,
            callsRemaining: callsRemaining,
            callsPercent: callsPercent,
            callsLabel: callsLabel,
            timestamp: timestamp
        )

        Task {
            if let existingActivity = Activity<TelecomWidgetAttributes>.activities.first(where: { $0.attributes.accountId == accountId }) {
                if #available(iOS 16.2, *) {
                    await existingActivity.update(ActivityContent(state: contentState, staleDate: nil))
                } else {
                    await existingActivity.update(using: contentState)
                }
                resolver(["status": "updated", "id": existingActivity.id])
            } else {
                // Keep a single clean active live widget
                for activity in Activity<TelecomWidgetAttributes>.activities {
                    if #available(iOS 16.2, *) {
                        await activity.end(nil, dismissalPolicy: .immediate)
                    } else {
                        await activity.end(using: nil, dismissalPolicy: .immediate)
                    }
                }

                let attributes = TelecomWidgetAttributes(accountId: accountId)
                do {
                    let activity: Activity<TelecomWidgetAttributes>
                    if #available(iOS 16.2, *) {
                        activity = try Activity.request(
                            attributes: attributes,
                            content: ActivityContent(state: contentState, staleDate: nil),
                            pushType: nil
                        )
                    } else {
                        activity = try Activity.request(
                            attributes: attributes,
                            contentState: contentState,
                            pushType: nil
                        )
                    }
                    resolver(["status": "started", "id": activity.id])
                } catch {
                    rejecter("REQUEST_FAILED", error.localizedDescription, error)
                }
            }
        }
    }

    @objc
    public func stopActivity(_ resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        guard #available(iOS 16.1, *) else {
            resolver(["status": "unsupported"])
            return
        }

        Task {
            for activity in Activity<TelecomWidgetAttributes>.activities {
                if #available(iOS 16.2, *) {
                    await activity.end(nil, dismissalPolicy: .immediate)
                } else {
                    await activity.end(using: nil, dismissalPolicy: .immediate)
                }
            }
            resolver(["status": "stopped"])
        }
    }

    @objc
    public func isActivityActive(_ resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        guard #available(iOS 16.1, *) else {
            resolver(false)
            return
        }
        let isActive = !Activity<TelecomWidgetAttributes>.activities.isEmpty
        resolver(isActive)
    }
}
