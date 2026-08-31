const { withInfoPlist, withEntitlementsPlist } = require('@expo/config-plugins');

const withTelecomWidgets = (config) => {
  // 1. Configure Entitlements with App Groups
  config = withEntitlementsPlist(config, (config) => {
    config.modResults['com.apple.security.application-groups'] = [
      'group.com.telecom.widget',
    ];
    return config;
  });

  // 2. Configure Info.plist with Live Activities
  config = withInfoPlist(config, (config) => {
    config.modResults['NSSupportsLiveActivities'] = true;
    config.modResults['NSSupportsLiveActivitiesFrequentUpdates'] = true;
    return config;
  });

  return config;
};

module.exports = withTelecomWidgets;
