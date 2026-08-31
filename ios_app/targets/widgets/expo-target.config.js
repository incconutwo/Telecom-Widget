/** @type {import('@bacons/apple-targets/app.plugin').Config} */
module.exports = {
  type: "widget",
  name: "telecomwidgetWidgets",
  displayName: "Telecom Widget",
  frameworks: [
    "SwiftUI",
    "WidgetKit",
    "ActivityKit",
    "AppIntents"
  ],
  entitlements: {
    "com.apple.security.application-groups": [
      "group.com.telecom.widget"
    ]
  },
  deploymentTarget: "17.0"
};
