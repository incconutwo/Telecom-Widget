import ExpoModulesCore
import ActivityKit
import WidgetKit
import Foundation

public class TelecomActivityModule: Module {
  public func definition() -> ModuleDefinition {
    Name("TelecomActivity")

    AsyncFunction("updateWidgetData") { (options: [String: Any]) in
      saveToUserDefaults(options: options)
      return ["status": "success"]
    }

    AsyncFunction("syncAllAccounts") { (options: [String: Any]) in
      if let userDefaults = UserDefaults(suiteName: "group.com.telecom.widget") {
        let activeAccountId = options["activeAccountId"] as? String ?? ""
        let accountsJson = options["accountsJson"] as? String ?? "[]"
        userDefaults.set(activeAccountId, forKey: "active_account_id")
        userDefaults.set(accountsJson, forKey: "all_accounts_data")
        userDefaults.synchronize()
        if #available(iOS 14.0, *) {
          WidgetCenter.shared.reloadAllTimelines()
        }
        return ["status": "synced"]
      } else {
        throw Exception(name: "APP_GROUP_ERROR", description: "Could not access App Group suite")
      }
    }

    AsyncFunction("startOrUpdateActivity") { (options: [String: Any]) in
      saveToUserDefaults(options: options)

      guard #available(iOS 16.1, *) else {
        throw Exception(name: "UNSUPPORTED_OS", description: "Live Activities require iOS 16.1 or later")
      }

      guard ActivityAuthorizationInfo().areActivitiesEnabled else {
        throw Exception(name: "DISABLED", description: "Live Activities are disabled in system settings")
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

      if let existingActivity = Activity<TelecomWidgetAttributes>.activities.first(where: { $0.attributes.accountId == accountId }) {
        if #available(iOS 16.2, *) {
          await existingActivity.update(ActivityContent(state: contentState, staleDate: nil))
        } else {
          await existingActivity.update(using: contentState)
        }
        return ["status": "updated", "id": existingActivity.id]
      } else {
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
          return ["status": "started", "id": activity.id]
        } catch {
          throw Exception(name: "REQUEST_FAILED", description: error.localizedDescription)
        }
      }
    }

    AsyncFunction("stopActivity") { () in
      guard #available(iOS 16.1, *) else {
        return ["status": "unsupported"]
      }
      for activity in Activity<TelecomWidgetAttributes>.activities {
        if #available(iOS 16.2, *) {
          await activity.end(nil, dismissalPolicy: .immediate)
        } else {
          await activity.end(using: nil, dismissalPolicy: .immediate)
        }
      }
      return ["status": "stopped"]
    }

    AsyncFunction("isActivityActive") { () -> Bool in
      guard #available(iOS 16.1, *) else {
        return false
      }
      return !Activity<TelecomWidgetAttributes>.activities.isEmpty
    }
  }
}

private func saveToUserDefaults(options: [String: Any]) {
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
