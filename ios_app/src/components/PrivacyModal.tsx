import React from 'react';
import {
  Modal,
  View,
  Text,
  StyleSheet,
  ScrollView,
  useColorScheme,
  Platform,
} from 'react-native';
import { SymbolView } from 'expo-symbols';
import { IOSNativeButton } from './IOSNativeButton';
import { t, isRTL } from '../utils/i18n';

interface PrivacyModalProps {
  visible: boolean;
  onClose: () => void;
}

export const PrivacyModal: React.FC<PrivacyModalProps> = ({ visible, onClose }) => {
  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  const colors = {
    background: isDark ? '#1C1C1E' : '#FFFFFF',
    textPrimary: isDark ? '#FFFFFF' : '#000000',
    textSecondary: isDark ? 'rgba(235, 235, 245, 0.65)' : 'rgba(60, 60, 67, 0.65)',
    blueBadge: isDark ? 'rgba(10, 132, 255, 0.15)' : 'rgba(0, 122, 255, 0.12)',
    greenBadge: isDark ? 'rgba(48, 209, 88, 0.15)' : 'rgba(52, 199, 89, 0.12)',
    orangeBadge: isDark ? 'rgba(255, 159, 10, 0.15)' : 'rgba(255, 149, 0, 0.12)',
    systemBlue: isDark ? '#0A84FF' : '#007AFF',
    systemGreen: isDark ? '#30D158' : '#34C759',
    systemOrange: isDark ? '#FF9F0A' : '#FF9500',
  };

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle={Platform.OS === 'ios' ? 'pageSheet' : 'fullScreen'}
      onRequestClose={onClose}
    >
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          bounces={true}
        >
          {/* Apple Hero Header */}
          <View style={styles.heroHeader}>
            <View style={[styles.heroIconContainer, { backgroundColor: colors.blueBadge }]}>
              <SymbolView name="checkmark.shield.fill" size={44} tintColor={colors.systemBlue} />
            </View>
            <Text style={[styles.heroTitle, { color: colors.textPrimary }]}>
              {t.privacyHeroTitle}
            </Text>
            <Text style={[styles.heroSubtitle, { color: colors.textSecondary }]}>
              {t.privacyHeroSubtitle}
            </Text>
          </View>

          {/* Feature List */}
          <View style={styles.featuresList}>
            <View style={styles.featureRow}>
              <View style={[styles.iconBox, { backgroundColor: colors.blueBadge }]}>
                <SymbolView name="key.fill" size={20} tintColor={colors.systemBlue} />
              </View>
              <View style={styles.featureTextCol}>
                <Text style={[styles.featureHeadline, { color: colors.textPrimary }]}>
                  {t.keychainTitle}
                </Text>
                <Text style={[styles.featureBody, { color: colors.textSecondary }]}>
                  {t.keychainDesc}
                </Text>
              </View>
            </View>

            <View style={styles.featureRow}>
              <View style={[styles.iconBox, { backgroundColor: colors.greenBadge }]}>
                <SymbolView name="network" size={20} tintColor={colors.systemGreen} />
              </View>
              <View style={styles.featureTextCol}>
                <Text style={[styles.featureHeadline, { color: colors.textPrimary }]}>
                  {t.noIntermediaryTitle}
                </Text>
                <Text style={[styles.featureBody, { color: colors.textSecondary }]}>
                  {t.noIntermediaryDesc}
                </Text>
              </View>
            </View>

            <View style={styles.featureRow}>
              <View style={[styles.iconBox, { backgroundColor: colors.orangeBadge }]}>
                <SymbolView name="chevron.left.forwardslash.chevron.right" size={18} tintColor={colors.systemOrange} />
              </View>
              <View style={styles.featureTextCol}>
                <Text style={[styles.featureHeadline, { color: colors.textPrimary }]}>
                  {t.openSourceTitle}
                </Text>
                <Text style={[styles.featureBody, { color: colors.textSecondary }]}>
                  {t.openSourceDesc}
                </Text>
              </View>
            </View>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <IOSNativeButton title={t.continue} onPress={onClose} />
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 24,
    paddingTop: 40,
    paddingBottom: 24,
  },
  heroHeader: {
    alignItems: 'center',
    marginBottom: 32,
  },
  heroIconContainer: {
    width: 72,
    height: 72,
    borderRadius: 36,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  heroTitle: {
    fontSize: 26,
    fontWeight: '700',
    letterSpacing: -0.4,
    textAlign: 'center',
    marginBottom: 8,
  },
  heroSubtitle: {
    fontSize: 15,
    fontWeight: '400',
    textAlign: 'center',
    lineHeight: 21,
    paddingHorizontal: 12,
  },
  featuresList: {
    gap: 24,
    marginBottom: 16,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 16,
  },
  iconBox: {
    width: 44,
    height: 44,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 2,
  },
  featureTextCol: {
    flex: 1,
  },
  featureHeadline: {
    fontSize: 16,
    fontWeight: '600',
    letterSpacing: -0.2,
    marginBottom: 3,
  },
  featureBody: {
    fontSize: 14,
    fontWeight: '400',
    lineHeight: 20,
  },
  footer: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: Platform.OS === 'ios' ? 34 : 16,
  },
});
