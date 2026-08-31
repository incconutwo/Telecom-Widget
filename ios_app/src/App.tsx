import React, { useEffect, useState, useCallback, useRef } from 'react';
import { View, StyleSheet, Platform, useColorScheme, AppState, AppStateStatus, Linking } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeBottomTabNavigator } from '@bottom-tabs/react-navigation';
import * as Haptics from 'expo-haptics';
import * as Updates from 'expo-updates';

import { LoginScreen } from './screens/LoginScreen';
import { DashboardScreen } from './screens/DashboardScreen';
import { SettingsModal } from './components/SettingsModal';
import { LockScreen } from './components/LockScreen';
import { StorageService } from './services/StorageService';
import { LiveActivityService } from './services/LiveActivityService';
import { SavedAccount } from './types';
import { isRTL, currentLanguage } from './utils/i18n';

// ─── Operator short names for native tab titles ───────────────────────────────
const getShortName = (operator: string): string => {
  if (currentLanguage === 'zgh') {
    switch (operator) {
      case 'Maroc Telecom': return 'ⵉⵜⵜⵉⵚⴰⵍⴰⵜ';
      case 'Orange': return 'ⵓⵕⴰⵏⵊ';
      case 'Inwi': return 'ⵉⵏⵡⵉ';
      default: return operator.slice(0, 6);
    }
  }
  switch (operator) {
    case 'Maroc Telecom': return isRTL ? 'اتصالات المغرب' : 'IAM';
    case 'Orange': return isRTL ? 'أورنج' : 'Orange';
    case 'Inwi': return isRTL ? 'إنوي' : 'Inwi';
    default: return operator.slice(0, 6);
  }
};

// ─── OS Version detection for Liquid Glass vs Legacy Edge-to-Edge ─────────────
const iosMajorVersion = Platform.OS === 'ios' ? parseInt(String(Platform.Version), 10) : 0;
const isLiquidGlassSupported = iosMajorVersion >= 26;

// ─── Native bottom tab navigator (rendered only when accounts >= 2) ───────────
const Tab = createNativeBottomTabNavigator();

interface AccountTabsProps {
  accounts: SavedAccount[];
  onAccountUpdated: (updated: SavedAccount) => void;
  onLogout: (acc: SavedAccount) => void;
  onOpenSettings: () => void;
  onAddNew: () => void;
}

function AccountTabs({
  accounts,
  onAccountUpdated,
  onLogout,
  onOpenSettings,
  onAddNew,
}: AccountTabsProps) {
  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  return (
    <Tab.Navigator
      tabBarActiveTintColor={isDark ? '#0A84FF' : '#007AFF'}
      tabBarInactiveTintColor={
        isDark ? 'rgba(235, 235, 245, 0.6)' : 'rgba(60, 60, 67, 0.6)'
      }
      scrollEdgeAppearance={isLiquidGlassSupported ? 'transparent' : 'default'}
      translucent={true}
      hapticFeedbackEnabled={true}
    >
      {accounts.map((acc) => (
        <Tab.Screen
          key={acc.id}
          name={acc.id}
          options={{
            title: getShortName(acc.operator),
            tabBarIcon: () => ({ sfSymbol: 'antenna.radiowaves.left.and.right' }),
          }}
        >
          {() => (
            <DashboardScreen
              account={acc}
              onOpenSettings={onOpenSettings}
              onAddNew={onAddNew}
              onAccountUpdated={onAccountUpdated}
              onLogout={() => onLogout(acc)}
            />
          )}
        </Tab.Screen>
      ))}
    </Tab.Navigator>
  );
}

// ─── Root App ─────────────────────────────────────────────────────────────────
export default function App() {
  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  const [accounts, setAccounts] = useState<SavedAccount[]>([]);
  const [activeAccountId, setActiveAccountId] = useState<string | null>(null);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isAddingNewAccount, setIsAddingNewAccount] = useState(false);
  const [isInitialized, setIsInitialized] = useState(false);
  const [isBiometricsEnabled, setIsBiometricsEnabled] = useState(false);
  const [isLocked, setIsLocked] = useState(false);

  useEffect(() => {
    loadSavedAccounts();

    // Check biometrics lock on launch
    StorageService.getBiometricsEnabled().then((enabled) => {
      setIsBiometricsEnabled(enabled);
      if (enabled) {
        setIsLocked(true);
      }
    });

    // Re-lock when app goes to background
    const appStateSubscription = AppState.addEventListener('change', (nextAppState: AppStateStatus) => {
      if (nextAppState === 'background' || nextAppState === 'inactive') {
        StorageService.getBiometricsEnabled().then((enabled) => {
          if (enabled) {
            setIsLocked(true);
          }
        });
      }
    });

    // Native Widget Deep Linking (e.g. telecomwidget://account/0610653694)
    const handleDeepLink = (event: { url: string }) => {
      const url = event.url;
      if (url && url.includes('telecomwidget://account/')) {
        const targetPhone = url.split('telecomwidget://account/')[1];
        if (targetPhone) {
          const cleanTarget = targetPhone.replace(/\D/g, '');
          setAccounts((currentAccounts) => {
            const match = currentAccounts.find((a) => {
              const accClean = (a.phone || a.cachedData?.phoneNumber || '').replace(/\D/g, '');
              return accClean.endsWith(cleanTarget) || cleanTarget.endsWith(accClean);
            });
            if (match) {
              setActiveAccountId(match.id);
              StorageService.setActiveAccountId(match.id);
            }
            return currentAccounts;
          });
        }
      }
    };

    const linkingSubscription = Linking.addEventListener('url', handleDeepLink);
    Linking.getInitialURL().then((url) => {
      if (url) handleDeepLink({ url });
    });

    // Auto-check and reload when new OTA update is published
    const checkForOTA = async () => {
      if (__DEV__) return;
      try {
        const update = await Updates.checkForUpdateAsync();
        if (update.isAvailable) {
          await Updates.fetchUpdateAsync();
          await Updates.reloadAsync();
        }
      } catch (_e) {}
    };
    checkForOTA();

    return () => {
      appStateSubscription.remove();
      linkingSubscription.remove();
    };
  }, []);

  const loadSavedAccounts = async () => {
    try {
      const list = await StorageService.getAccounts();
      setAccounts(list);
      const savedActiveId = await StorageService.getActiveAccountId();
      const active = list.find((a) => a.id === savedActiveId) || list[0] || null;
      setActiveAccountId(active?.id || null);

      if (active) {
        LiveActivityService.syncAllAccounts(list, active.id);
        if (active.liveNotificationEnabled) {
          LiveActivityService.startOrUpdateLiveActivity(active);
        }
      }
    } catch (_e) {
    } finally {
      setIsInitialized(true);
    }
  };

  const handleLoginSuccess = async (newAccount: SavedAccount) => {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    const updated = [...accounts.filter((a) => a.id !== newAccount.id), newAccount];
    setAccounts(updated);
    setActiveAccountId(newAccount.id);
    setIsAddingNewAccount(false);
    await StorageService.saveAccounts(updated);
    await StorageService.setActiveAccountId(newAccount.id);

    // Keep all Home Screen widgets and Live Activity synchronized
    LiveActivityService.syncAllAccounts(updated, newAccount.id);
    if (newAccount.liveNotificationEnabled) {
      LiveActivityService.startOrUpdateLiveActivity(newAccount);
    }
  };

  const handleAccountUpdated = async (updated: SavedAccount) => {
    const next = accounts.map((a) => (a.id === updated.id ? updated : a));
    setAccounts(next);
    await StorageService.saveAccounts(next);
    LiveActivityService.syncAllAccounts(next, activeAccountId);
    if (updated.liveNotificationEnabled) {
      LiveActivityService.startOrUpdateLiveActivity(updated);
    }
  };

  const handleDeleteAccount = async (target: SavedAccount) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const next = accounts.filter((a) => a.id !== target.id);
    setAccounts(next);
    await StorageService.saveAccounts(next);

    const newActive = activeAccountId === target.id ? next[0]?.id || null : activeAccountId;
    if (activeAccountId === target.id) {
      setActiveAccountId(newActive);
      if (newActive) await StorageService.setActiveAccountId(newActive);
    }

    LiveActivityService.syncAllAccounts(next, newActive);

    if (next.length === 0) {
      LiveActivityService.stopLiveActivity();
    }
  };

  const handleToggleLiveActivity = async (acc: SavedAccount, enabled: boolean) => {
    const updated = { ...acc, liveNotificationEnabled: enabled };
    await handleAccountUpdated(updated);
    if (enabled) {
      LiveActivityService.startOrUpdateLiveActivity(updated);
    } else {
      LiveActivityService.stopLiveActivity();
    }
  };

  if (!isInitialized) {
    return <View style={[styles.container, { backgroundColor: isDark ? '#000000' : '#F2F2F7' }]} />;
  }

  const activeAccount = accounts.find((a) => a.id === activeAccountId) || accounts[0];
  const showLogin = accounts.length === 0 || isAddingNewAccount;

  return (
    <View style={[styles.container, { backgroundColor: isDark ? '#000000' : '#F2F2F7' }]}>
      <StatusBar style={isDark ? 'light' : 'dark'} />

      {showLogin ? (
        <LoginScreen
          onLoginSuccess={handleLoginSuccess}
          canCancel={accounts.length > 0 && isAddingNewAccount}
          onCancel={() => setIsAddingNewAccount(false)}
        />
      ) : accounts.length === 1 ? (
        /* Single Account Mode: Full Screen edge-to-edge dashboard (no bottom tab bar) */
        <DashboardScreen
          account={activeAccount}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onAddNew={() => setIsAddingNewAccount(true)}
          onAccountUpdated={handleAccountUpdated}
          onLogout={() => handleDeleteAccount(activeAccount)}
        />
      ) : (
        /* Multi-Account Mode (2+ accounts): Native Apple Liquid Glass Tab Bar */
        <NavigationContainer>
          <AccountTabs
            accounts={accounts}
            onAccountUpdated={handleAccountUpdated}
            onLogout={handleDeleteAccount}
            onOpenSettings={() => setIsSettingsOpen(true)}
            onAddNew={() => setIsAddingNewAccount(true)}
          />
        </NavigationContainer>
      )}

      {/* Apple HIG Inset-Grouped Settings & Account Management Sheet */}
      <SettingsModal
        visible={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        accounts={accounts}
        activeAccountId={activeAccountId}
        isBiometricsEnabled={isBiometricsEnabled}
        onToggleBiometrics={(enabled) => setIsBiometricsEnabled(enabled)}
        onSelectAccount={(acc) => {
          setActiveAccountId(acc.id);
          StorageService.setActiveAccountId(acc.id);
          setIsSettingsOpen(false);
        }}
        onAddNew={() => {
          setIsSettingsOpen(false);
          setIsAddingNewAccount(true);
        }}
        onDeleteAccount={handleDeleteAccount}
        onToggleLiveActivity={handleToggleLiveActivity}
      />

      {/* Native Apple Biometric Lock Screen Presentation */}
      {isLocked && (
        <LockScreen onUnlock={() => setIsLocked(false)} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
