import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  StyleSheet,
  Pressable,
  ScrollView,
  Switch,
  Alert,
  ActionSheetIOS,
  Platform,
  useColorScheme,
} from 'react-native';
import { SymbolView } from 'expo-symbols';
import * as Haptics from 'expo-haptics';
import { SavedAccount } from '../types';
import { PrivacyModal } from './PrivacyModal';
import { StorageService } from '../services/StorageService';
import { BiometricsService, BiometricType } from '../services/BiometricsService';
import { t, isRTL } from '../utils/i18n';
import { formatPhoneNumber } from '../utils/formatters';

interface SettingsModalProps {
  visible: boolean;
  onClose: () => void;
  accounts: SavedAccount[];
  activeAccountId: string | null;
  onSelectAccount: (acc: SavedAccount) => void;
  onAddNew: () => void;
  onDeleteAccount: (acc: SavedAccount) => void;
  onToggleLiveActivity: (acc: SavedAccount, enabled: boolean) => void;
  isBiometricsEnabled?: boolean;
  onToggleBiometrics?: (enabled: boolean) => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  visible,
  onClose,
  accounts,
  activeAccountId,
  onSelectAccount,
  onAddNew,
  onDeleteAccount,
  onToggleLiveActivity,
  isBiometricsEnabled: parentBiometricsEnabled,
  onToggleBiometrics,
}) => {
  const [privacyVisible, setPrivacyVisible] = useState(false);
  const [biometricsEnabled, setBiometricsEnabled] = useState(parentBiometricsEnabled ?? false);
  const [isBiometricsAvailable, setIsBiometricsAvailable] = useState(false);
  const [biometricType, setBiometricType] = useState<BiometricType>('faceId');

  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  useEffect(() => {
    BiometricsService.isAvailable().then(setIsBiometricsAvailable);
    BiometricsService.getBiometricType().then(setBiometricType);
    StorageService.getBiometricsEnabled().then(setBiometricsEnabled);
  }, [visible]);

  const handleToggleBiometrics = async (enabled: boolean) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    if (enabled) {
      // Authenticate to confirm user identity before enabling
      const success = await BiometricsService.authenticate(t.unlockPrompt);
      if (!success) {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
        return;
      }
    }

    setBiometricsEnabled(enabled);
    await StorageService.setBiometricsEnabled(enabled);
    onToggleBiometrics?.(enabled);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  };

  const colors = {
    background: isDark ? '#000000' : '#F2F2F7',
    card: isDark ? '#1C1C1E' : '#FFFFFF',
    textPrimary: isDark ? '#FFFFFF' : '#000000',
    textSecondary: isDark ? 'rgba(235, 235, 245, 0.6)' : 'rgba(60, 60, 67, 0.6)',
    divider: isDark ? 'rgba(255, 255, 255, 0.15)' : 'rgba(60, 60, 67, 0.15)',
    systemBlue: isDark ? '#0A84FF' : '#007AFF',
    systemGreen: isDark ? '#30D158' : '#34C759',
    systemOrange: isDark ? '#FF9F0A' : '#FF9500',
    systemRed: isDark ? '#FF453A' : '#FF3B30',
  };

  const activeAccount = accounts.find((a) => a.id === activeAccountId) || accounts[0];

  const handleClose = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    onClose();
  };

  const handleDelete = (acc: SavedAccount) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const title = t.removeAccountTitle;
    const identifier = formatPhoneNumber(acc.phone) || acc.email;
    const message = t.removeAccountMessage(acc.operator, identifier);

    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions(
        {
          title,
          message,
          options: [t.cancel, t.removeAccountTitle],
          destructiveButtonIndex: 1,
          cancelButtonIndex: 0,
        },
        (buttonIndex) => {
          if (buttonIndex === 1) {
            onDeleteAccount(acc);
            if (accounts.length <= 1) {
              onClose();
            }
          }
        }
      );
    } else {
      Alert.alert(title, message, [
        { text: t.cancel, style: 'cancel' },
        {
          text: t.removeAccountTitle,
          style: 'destructive',
          onPress: () => {
            onDeleteAccount(acc);
            if (accounts.length <= 1) {
              onClose();
            }
          },
        },
      ]);
    }
  };

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle={Platform.OS === 'ios' ? 'pageSheet' : 'fullScreen'}
      onRequestClose={onClose}
    >
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        {/* Navigation Bar */}
        <View style={styles.navBar}>
          <Text style={[styles.navTitle, { color: colors.textPrimary }]}>{t.settings}</Text>
          <Pressable
            onPress={handleClose}
            style={({ pressed }) => [
              styles.doneButton,
              pressed && styles.buttonPressed,
            ]}
            accessibilityRole="button"
            accessibilityLabel={t.done}
            hitSlop={8}
          >
            <Text style={[styles.doneText, { color: colors.systemBlue }]}>{t.done}</Text>
          </Pressable>
        </View>

        <ScrollView
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Section 1: Connected Accounts */}
          <View style={styles.section}>
            <View style={styles.sectionHeaderRow}>
              <Text style={[styles.sectionHeaderTitle, { color: colors.textSecondary }]}>{t.accounts}</Text>
              <Pressable
                onPress={() => {
                  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                  onClose();
                  onAddNew();
                }}
                style={({ pressed }) => [
                  styles.addAccountHeaderBtn,
                  pressed && styles.buttonPressed,
                ]}
                hitSlop={8}
              >
                <SymbolView name="plus" size={14} tintColor={colors.systemBlue} />
                <Text style={[styles.addAccountHeaderText, { color: colors.systemBlue }]}>{t.add}</Text>
              </Pressable>
            </View>

            <View style={[styles.groupedCard, { backgroundColor: colors.card }]}>
              {accounts.map((acc, index) => {
                const isActive = acc.id === activeAccountId;
                const isLast = index === accounts.length - 1;

                return (
                  <View key={acc.id}>
                    <Pressable
                      onPress={() => {
                        Haptics.selectionAsync();
                        onSelectAccount(acc);
                      }}
                      style={({ pressed }) => [
                        styles.row,
                        pressed && styles.rowPressed,
                      ]}
                    >
                      <View style={[styles.iconBox, { backgroundColor: colors.systemBlue }]}>
                        <SymbolView name="antenna.radiowaves.left.and.right" size={16} tintColor="#FFFFFF" />
                      </View>

                      <View style={styles.rowTextCol}>
                        <View style={styles.accountTitleRow}>
                          <Text style={[styles.rowTitle, { color: colors.textPrimary }]}>{acc.operator}</Text>
                          {isActive && (
                            <View style={[styles.activePill, { backgroundColor: `${colors.systemBlue}20` }]}>
                              <Text style={[styles.activePillText, { color: colors.systemBlue }]}>{t.active}</Text>
                            </View>
                          )}
                        </View>
                        <Text style={[styles.rowSubtitle, { color: colors.textSecondary }]}>
                          {formatPhoneNumber(acc.phone) || acc.email}
                        </Text>
                      </View>

                      <Pressable
                        onPress={() => handleDelete(acc)}
                        hitSlop={12}
                        style={({ pressed }) => [
                          styles.deleteBtn,
                          pressed && styles.buttonPressed,
                        ]}
                      >
                        <SymbolView name="trash.fill" size={17} tintColor={colors.systemRed} />
                      </Pressable>
                    </Pressable>

                    {!isLast && <View style={[styles.innerDivider, { backgroundColor: colors.divider }]} />}
                  </View>
                );
              })}
            </View>
          </View>

          {/* Section 2: Security & Biometrics */}
          {isBiometricsAvailable && (
            <View style={styles.section}>
              <Text style={[styles.sectionHeaderTitle, { color: colors.textSecondary }]}>{t.security}</Text>
              <View style={[styles.groupedCard, { backgroundColor: colors.card }]}>
                <View style={styles.row}>
                  <View style={[styles.iconBox, { backgroundColor: colors.systemBlue }]}>
                    <SymbolView
                      name={biometricType === 'faceId' ? 'faceid' : biometricType === 'touchId' ? 'touchid' : 'lock.fill'}
                      size={16}
                      tintColor="#FFFFFF"
                    />
                  </View>
                  <View style={styles.rowTextCol}>
                    <Text style={[styles.rowTitle, { color: colors.textPrimary }]}>
                      {biometricType === 'faceId' ? t.faceId : biometricType === 'touchId' ? t.touchId : t.security}
                    </Text>
                    <Text style={[styles.rowSubtitle, { color: colors.textSecondary }]}>
                      {t.biometricsSubtitle}
                    </Text>
                  </View>
                  <Switch
                    value={biometricsEnabled}
                    onValueChange={handleToggleBiometrics}
                  />
                </View>
              </View>
            </View>
          )}

          {/* Section 3: Live Activities & Widgets */}
          {activeAccount && (
            <View style={styles.section}>
              <Text style={[styles.sectionHeaderTitle, { color: colors.textSecondary }]}>{t.liveActivities}</Text>
              <View style={[styles.groupedCard, { backgroundColor: colors.card }]}>
                <View style={styles.row}>
                  <View style={[styles.iconBox, { backgroundColor: colors.systemOrange }]}>
                    <SymbolView name="bell.badge.fill" size={16} tintColor="#FFFFFF" />
                  </View>
                  <View style={styles.rowTextCol}>
                    <Text style={[styles.rowTitle, { color: colors.textPrimary }]}>{t.liveStatusTitle}</Text>
                    <Text style={[styles.rowSubtitle, { color: colors.textSecondary }]}>
                      {t.liveStatusSubtitle}
                    </Text>
                  </View>
                  <Switch
                    value={!!activeAccount.liveNotificationEnabled}
                    onValueChange={(val) => {
                      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                      onToggleLiveActivity(activeAccount, val);
                    }}
                  />
                </View>
              </View>
            </View>
          )}

          {/* Section 4: Privacy & About */}
          <View style={styles.section}>
            <Text style={[styles.sectionHeaderTitle, { color: colors.textSecondary }]}>{t.about}</Text>
            <View style={[styles.groupedCard, { backgroundColor: colors.card }]}>
              <Pressable
                onPress={() => {
                  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                  setPrivacyVisible(true);
                }}
                style={({ pressed }) => [
                  styles.row,
                  pressed && styles.rowPressed,
                ]}
              >
                <View style={[styles.iconBox, { backgroundColor: colors.systemGreen }]}>
                  <SymbolView name="checkmark.shield.fill" size={16} tintColor="#FFFFFF" />
                </View>
                <View style={styles.rowTextCol}>
                  <Text style={[styles.rowTitle, { color: colors.textPrimary }]}>{t.privacyAndSecurity}</Text>
                  <Text style={[styles.rowSubtitle, { color: colors.textSecondary }]}>{t.privacySubtitle}</Text>
                </View>
                <SymbolView name="chevron.right" size={14} tintColor={colors.textSecondary} />
              </Pressable>

              <View style={[styles.innerDivider, { backgroundColor: colors.divider }]} />

              <View style={styles.row}>
                <Text style={[styles.infoLabel, { color: colors.textPrimary }]}>{t.appVersion}</Text>
                <Text style={[styles.infoValue, { color: colors.textSecondary }]}>1.0.0 (Native SF Symbols)</Text>
              </View>
            </View>
          </View>
        </ScrollView>
      </View>

      {/* Embedded Privacy Sheet */}
      <PrivacyModal
        visible={privacyVisible}
        onClose={() => setPrivacyVisible(false)}
      />
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  navBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 24,
    paddingBottom: 16,
  },
  navTitle: {
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: -0.4,
  },
  doneButton: {
    paddingVertical: 6,
    paddingHorizontal: 8,
  },
  doneText: {
    fontSize: 17,
    fontWeight: '600',
  },
  buttonPressed: {
    opacity: 0.65,
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 36,
  },
  section: {
    marginBottom: 28,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  sectionHeaderTitle: {
    fontSize: 13,
    fontWeight: '400',
    letterSpacing: -0.08,
    textTransform: 'uppercase',
  },
  addAccountHeaderBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  addAccountHeaderText: {
    fontSize: 14,
    fontWeight: '500',
  },
  groupedCard: {
    borderRadius: 12,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 11,
    paddingHorizontal: 16,
    minHeight: 48,
  },
  rowPressed: {
    opacity: 0.7,
  },
  iconBox: {
    width: 30,
    height: 30,
    borderRadius: 7,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  rowTextCol: {
    flex: 1,
    marginRight: 10,
  },
  accountTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '400',
    letterSpacing: -0.2,
  },
  rowSubtitle: {
    fontSize: 13,
    fontWeight: '400',
    marginTop: 1,
  },
  activePill: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  activePillText: {
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.1,
  },
  deleteBtn: {
    padding: 6,
  },
  innerDivider: {
    height: StyleSheet.hairlineWidth,
    marginLeft: 58,
  },
  infoLabel: {
    flex: 1,
    fontSize: 16,
    fontWeight: '400',
  },
  infoValue: {
    letterSpacing: -0.2,
  },
});
