import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Pressable, useColorScheme } from 'react-native';
import { SymbolView } from 'expo-symbols';
import * as Haptics from 'expo-haptics';
import { IOSNativeButton } from './IOSNativeButton';
import { BiometricsService, BiometricType } from '../services/BiometricsService';
import { t } from '../utils/i18n';

interface LockScreenProps {
  onUnlock: () => void;
}

export const LockScreen: React.FC<LockScreenProps> = ({ onUnlock }) => {
  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  const [biometricType, setBiometricType] = useState<BiometricType>('faceId');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  const colors = {
    background: isDark ? '#000000' : '#F2F2F7',
    card: isDark ? '#1C1C1E' : '#FFFFFF',
    textPrimary: isDark ? '#FFFFFF' : '#000000',
    textSecondary: isDark ? 'rgba(235, 235, 245, 0.6)' : 'rgba(60, 60, 67, 0.6)',
    systemBlue: isDark ? '#0A84FF' : '#007AFF',
    badgeBg: isDark ? 'rgba(10, 132, 255, 0.15)' : 'rgba(0, 122, 255, 0.12)',
  };

  useEffect(() => {
    BiometricsService.getBiometricType().then(setBiometricType);
    handleAuthenticate();
  }, []);

  const handleAuthenticate = async () => {
    if (isAuthenticating) return;
    setIsAuthenticating(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);

    try {
      const success = await BiometricsService.authenticate(t.unlockPrompt);
      if (success) {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        onUnlock();
      } else {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      }
    } finally {
      setIsAuthenticating(false);
    }
  };

  const symbolIconName =
    biometricType === 'faceId' ? 'faceid' : biometricType === 'touchId' ? 'touchid' : 'lock.fill';

  const unlockButtonLabel =
    biometricType === 'faceId'
      ? t.unlockApp
      : biometricType === 'touchId'
      ? `${t.unlockApp.replace(/Face ID/i, 'Touch ID')}`
      : t.unlockApp;

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.content}>
        <Pressable onPress={handleAuthenticate} style={styles.iconWrapper} hitSlop={16}>
          <View style={[styles.iconBadge, { backgroundColor: colors.badgeBg }]}>
            <SymbolView name={symbolIconName as any} size={54} tintColor={colors.systemBlue} />
          </View>
        </Pressable>

        <Text style={[styles.title, { color: colors.textPrimary }]}>{t.appName}</Text>
        <Text style={[styles.subtitle, { color: colors.textSecondary }]}>
          {t.authenticateToUnlock}
        </Text>
      </View>

      <View style={styles.footer}>
        <IOSNativeButton
          title={unlockButtonLabel}
          onPress={handleAuthenticate}
          isLoading={isAuthenticating}
          style={styles.unlockBtn}
        />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 99999,
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingTop: 120,
    paddingBottom: 48,
  },
  content: {
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
  },
  iconWrapper: {
    marginBottom: 28,
  },
  iconBadge: {
    width: 100,
    height: 100,
    borderRadius: 50,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: -0.4,
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 15,
    fontWeight: '400',
    textAlign: 'center',
    paddingHorizontal: 32,
    lineHeight: 20,
  },
  footer: {
    width: '100%',
  },
  unlockBtn: {
    width: '100%',
  },
});
