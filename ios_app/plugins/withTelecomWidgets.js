const { withInfoPlist, withEntitlementsPlist, withDangerousMod, withXcodeProject } = require('@expo/config-plugins');
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

      if (!fs.existsSync(widgetExtensionDir)) {
        fs.mkdirSync(widgetExtensionDir, { recursive: true });
      }

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

      // Copy all widget swift files and plists to widget extension directory
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

  // 4. Register the Widget Extension Native Target in the Xcode Project
  config = withXcodeProject(config, (config) => {
    const xcodeProject = config.modResults;
    const projectName = config.modRequest.projectName || 'telecomwidget';
    const widgetTargetName = `${projectName}Widgets`;
    const bundleIdentifier = 'com.telecom.widget.TelecomWidgetExtension';

    // Check if target already exists
    const existingTarget = xcodeProject.pbxTargetByName(widgetTargetName);
    if (!existingTarget) {
      const widgetFiles = [
        'TelecomWidgetBundle.swift',
        'TelecomHomeScreenWidget.swift',
        'TelecomLiveActivityWidget.swift',
        'TelecomWidgetAttributes.swift',
        'TelecomAppIntent.swift',
      ];

      // Add target of type app_extension
      const target = xcodeProject.addTarget(
        widgetTargetName,
        'app_extension',
        widgetTargetName,
        bundleIdentifier
      );

      // Create build phases
      xcodeProject.addBuildPhase(
        widgetFiles,
        'PBXSourcesBuildPhase',
        'Sources',
        target.uuid
      );
      xcodeProject.addBuildPhase(
        [],
        'PBXFrameworksBuildPhase',
        'Frameworks',
        target.uuid
      );
      xcodeProject.addBuildPhase(
        [],
        'PBXResourcesBuildPhase',
        'Resources',
        target.uuid
      );

      // Update build configurations for widget extension
      const configurations = xcodeProject.pbxXCBuildConfigurationSection();
      for (const key in configurations) {
        if (typeof configurations[key] === 'object' && configurations[key].buildSettings) {
          const settings = configurations[key].buildSettings;
          if (settings.PRODUCT_NAME === `"${widgetTargetName}"` || settings.PRODUCT_NAME === widgetTargetName) {
            settings.SWIFT_VERSION = '5.0';
            settings.IPHONEOS_DEPLOYMENT_TARGET = '16.1';
            settings.PRODUCT_BUNDLE_IDENTIFIER = `"${bundleIdentifier}"`;
            settings.CODE_SIGN_ENTITLEMENTS = `"${widgetTargetName}/telecomwidgetWidgets.entitlements"`;
            settings.INFOPLIST_FILE = `"${widgetTargetName}/Info.plist"`;
            settings.GENERATE_INFOPLIST_FILE = 'NO';
            settings.CURRENT_PROJECT_VERSION = '1.0.0';
            settings.MARKETING_VERSION = '1.0.0';
            settings.SWIFT_OPTIMIZATION_LEVEL = '-O';
          }
        }
      }

      // Add widget extension copy files build phase to main target (Embed App Extensions into PlugIns)
      const mainTargetUuid = xcodeProject.getFirstTarget().uuid;
      xcodeProject.addBuildPhase(
        [`${widgetTargetName}.appex`],
        'PBXCopyFilesBuildPhase',
        'Embed App Extensions',
        mainTargetUuid,
        'app_extension'
      );
    }

    return config;
  });

  return config;
};

module.exports = withTelecomWidgets;
