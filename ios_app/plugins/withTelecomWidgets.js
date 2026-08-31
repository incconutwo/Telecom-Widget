const { withInfoPlist, withEntitlementsPlist, withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

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

  // 3. Inject Native Swift Module Files & Podfile configuration
  config = withDangerousMod(config, [
    'ios',
    async (config) => {
      const iosRoot = config.modRequest.platformProjectRoot;
      const projectName = config.modRequest.projectName || 'telecomwidget';
      const projectDir = path.join(iosRoot, projectName);
      const widgetExtensionDir = path.join(iosRoot, `${projectName}Widgets`);

      // Copy native module files to main app target directory
      const nativeModulesSrc = path.join(config.modRequest.projectRoot, 'targets', 'native_modules');
      if (fs.existsSync(nativeModulesSrc) && fs.existsSync(projectDir)) {
        const files = fs.readdirSync(nativeModulesSrc);
        for (const file of files) {
          fs.copyFileSync(path.join(nativeModulesSrc, file), path.join(projectDir, file));
        }
      }

      // Copy TelecomWidgetAttributes.swift into Main App so ActivityKit finds attributes in scope
      const attributesSrc = path.join(config.modRequest.projectRoot, 'targets', 'widgets', 'TelecomWidgetAttributes.swift');
      if (fs.existsSync(attributesSrc) && fs.existsSync(projectDir)) {
        fs.copyFileSync(attributesSrc, path.join(projectDir, 'TelecomWidgetAttributes.swift'));
      }

      // Copy all widget swift files to widget extension directory
      const widgetsSrc = path.join(config.modRequest.projectRoot, 'targets', 'widgets');
      if (fs.existsSync(widgetsSrc) && fs.existsSync(widgetExtensionDir)) {
        const files = fs.readdirSync(widgetsSrc);
        for (const file of files) {
          fs.copyFileSync(path.join(widgetsSrc, file), path.join(widgetExtensionDir, file));
        }
      }

      // Configure Podfile for Swift compilation & Live Activities
      const podfilePath = path.join(iosRoot, 'Podfile');
      if (fs.existsSync(podfilePath)) {
        let content = fs.readFileSync(podfilePath, 'utf8');
        const patch = `
    installer.pods_project.targets.each do |target|
      target.build_configurations.each do |config|
        config.build_settings['SWIFT_EMIT_MODULE_INTERFACE'] = 'NO'
        config.build_settings['SWIFT_COMPILATION_MODE'] = 'wholemodule'
        config.build_settings['SWIFT_STRICT_CONCURRENCY'] = 'minimal'
      end
    end
`;
        if (!content.includes('SWIFT_EMIT_MODULE_INTERFACE')) {
          content = content.replace(
            /post_install do \|installer\|/,
            `post_install do |installer|${patch}`
          );
          fs.writeFileSync(podfilePath, content, 'utf8');
        }
      }
      return config;
    },
  ]);

  return config;
};

module.exports = withTelecomWidgets;
