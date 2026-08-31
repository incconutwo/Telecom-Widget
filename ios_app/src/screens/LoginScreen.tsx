import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  Pressable,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  useColorScheme,
} from 'react-native';
import { SymbolView } from 'expo-symbols';
import * as Haptics from 'expo-haptics';
import { PrivacyModal } from '../components/PrivacyModal';
import SegmentedControl from '@react-native-segmented-control/segmented-control';
import { IOSNativeButton } from '../components/IOSNativeButton';
import { MarocTelecomClient } from '../services/MarocTelecomClient';
import { OrangeClient } from '../services/OrangeClient';
import { InwiClient } from '../services/InwiClient';
import { SavedAccount, ConsumptionData } from '../types';
import { t, isRTL } from '../utils/i18n';

interface LoginScreenProps {
  onLoginSuccess: (account: SavedAccount) => void;
  canCancel?: boolean;
  onCancel?: () => void;
}

const OPERATORS = ['Maroc Telecom', 'Orange', 'Inwi'];

export const LoginScreen: React.FC<LoginScreenProps> = ({
  onLoginSuccess,
  canCancel = false,
  onCancel,
}) => {
  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  const colors = {
    background: isDark ? '#000000' : '#F2F2F7',
    groupedCard: isDark ? '#1C1C1E' : '#FFFFFF',
    textPrimary: isDark ? '#FFFFFF' : '#000000',
    textSecondary: isDark ? 'rgba(235, 235, 245, 0.6)' : 'rgba(60, 60, 67, 0.6)',
    divider: isDark ? 'rgba(255, 255, 255, 0.15)' : 'rgba(60, 60, 67, 0.15)',
    icon: isDark ? 'rgba(235, 235, 245, 0.6)' : 'rgba(60, 60, 67, 0.6)',
    placeholder: isDark ? 'rgba(235, 235, 245, 0.35)' : 'rgba(60, 60, 67, 0.35)',
    infoBtnBg: isDark ? 'rgba(10, 132, 255, 0.15)' : 'rgba(0, 122, 255, 0.10)',
    navBlue: isDark ? '#0A84FF' : '#007AFF',
  };

  const [operator, setOperator] = useState('Maroc Telecom');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [privacyVisible, setPrivacyVisible] = useState(false);

  // Native input focus references for smooth return key transitions
  const emailInputRef = useRef<TextInput>(null);
  const phoneInputRef = useRef<TextInput>(null);
  const passwordInputRef = useRef<TextInput>(null);

  const handleLogin = async () => {
    if (!email && !phone) {
      setError(t.errorEmptyCredentials);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      return;
    }
    if (!password) {
      setError(t.errorEmptyPassword);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      return;
    }

    setIsLoading(true);
    setError(null);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);

    try {
      let data: ConsumptionData;
      let cookies: string[] = [];

      if (operator === 'Maroc Telecom') {
        const client = new MarocTelecomClient(email, password, phone);
        data = await client.fetchConsumption();
        cookies = client.currentCookies;
      } else if (operator === 'Orange') {
        const client = new OrangeClient(email, password, phone || email);
        data = await client.fetchConsumption();
        cookies = client.currentCookies;
      } else {
        const client = new InwiClient(phone || email, password, phone || email);
        data = await client.fetchConsumption();
        cookies = client.currentCookies;
      }

      const newAccount: SavedAccount = {
        id: `${operator}_${phone || email}_${Date.now()}`,
        operator,
        email,
        phone: phone || data.phoneNumber,
        password,
        cookies,
        cachedData: data,
        lastUpdated: Date.now(),
        liveNotificationEnabled: true,
      };

      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      onLoginSuccess(newAccount);
    } catch (e: any) {
      setError(e.message || t.loginFailed);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={[styles.container, { backgroundColor: colors.background }]}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {/* Apple HIG Top Navigation Bar */}
        <View style={styles.topNavRow}>
          {canCancel ? (
            <Pressable
              onPress={() => {
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                onCancel?.();
              }}
              style={styles.backBtn}
              hitSlop={12}
              accessibilityRole="button"
              accessibilityLabel="Back"
            >
              <SymbolView name="chevron.left" size={20} tintColor={colors.navBlue} />
              <Text style={[styles.backText, { color: colors.navBlue }]}>{t.accounts}</Text>
            </Pressable>
          ) : (
            <View style={styles.navPlaceholder} />
          )}

          <Pressable
            onPress={() => {
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
              setPrivacyVisible(true);
            }}
            style={[styles.infoCircleBtn, { backgroundColor: colors.infoBtnBg }]}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={t.privacyAndSecurity}
          >
            <SymbolView name="info.circle" size={18} tintColor={colors.navBlue} />
          </Pressable>
        </View>

        <View style={styles.titleContainer}>
          <Text style={[styles.screenTitle, { color: colors.textPrimary }]}>
            {canCancel ? t.addAccountTitle : t.welcome}
          </Text>
          <Text style={[styles.screenSubtitle, { color: colors.textSecondary }]}>
            {canCancel ? t.addAccountSubtitle : t.welcomeSubtitle}
          </Text>
        </View>

        <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>{t.operator}</Text>

        {/* Native iOS Segmented Control */}
        <SegmentedControl
          values={OPERATORS}
          selectedIndex={OPERATORS.indexOf(operator)}
          onChange={(event) => {
            Haptics.selectionAsync();
            setOperator(event.nativeEvent.value);
            setError(null);
          }}
          appearance={colorScheme ?? 'dark'}
          style={styles.segmentedControl}
        />

        <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>{t.credentials}</Text>

        {/* Inset-Grouped Form Container */}
        <View style={[styles.groupedCard, { backgroundColor: colors.groupedCard }]}>
          {/* Email or Username Row */}
          <View style={styles.formRow}>
            <View style={styles.inputLeadingIcon}>
              <SymbolView
                name={operator === 'Maroc Telecom' ? 'envelope.fill' : 'globe'}
                size={18}
                tintColor={colors.icon}
              />
            </View>
            <TextInput
              ref={emailInputRef}
              value={email}
              onChangeText={setEmail}
              placeholder={operator === 'Maroc Telecom' ? t.email : t.emailOrPhone}
              placeholderTextColor={colors.placeholder}
              autoCapitalize="none"
              autoCorrect={false}
              spellCheck={false}
              textContentType={operator === 'Maroc Telecom' ? 'emailAddress' : 'username'}
              autoComplete={operator === 'Maroc Telecom' ? 'email' : 'username'}
              keyboardType={operator === 'Maroc Telecom' ? 'email-address' : 'default'}
              returnKeyType="next"
              enablesReturnKeyAutomatically={true}
              clearButtonMode="while-editing"
              onSubmitEditing={() => {
                if (operator === 'Maroc Telecom') {
                  phoneInputRef.current?.focus();
                } else {
                  passwordInputRef.current?.focus();
                }
              }}
              style={[styles.textInput, { color: colors.textPrimary }]}
            />
          </View>

          {/* Phone Number (For IAM) */}
          {operator === 'Maroc Telecom' && (
            <>
              <View style={[styles.rowDivider, { backgroundColor: colors.divider }]} />
              <View style={styles.formRow}>
                <View style={styles.inputLeadingIcon}>
                  <SymbolView name="phone.fill" size={18} tintColor={colors.icon} />
                </View>
                <TextInput
                  ref={phoneInputRef}
                  value={phone}
                  onChangeText={setPhone}
                  placeholder={t.phone}
                  placeholderTextColor={colors.placeholder}
                  keyboardType="phone-pad"
                  autoCapitalize="none"
                  autoCorrect={false}
                  spellCheck={false}
                  textContentType="telephoneNumber"
                  autoComplete="tel"
                  returnKeyType="next"
                  enablesReturnKeyAutomatically={true}
                  clearButtonMode="while-editing"
                  onSubmitEditing={() => passwordInputRef.current?.focus()}
                  style={[styles.textInput, { color: colors.textPrimary }]}
                />
              </View>
            </>
          )}

          <View style={[styles.rowDivider, { backgroundColor: colors.divider }]} />

          {/* Password Row */}
          <View style={styles.formRow}>
            <View style={styles.inputLeadingIcon}>
              <SymbolView name="lock.fill" size={18} tintColor={colors.icon} />
            </View>
            <TextInput
              ref={passwordInputRef}
              value={password}
              onChangeText={setPassword}
              placeholder={t.password}
              placeholderTextColor={colors.placeholder}
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              autoCorrect={false}
              spellCheck={false}
              textContentType="password"
              autoComplete="current-password"
              returnKeyType="go"
              enablesReturnKeyAutomatically={true}
              onSubmitEditing={handleLogin}
              style={[styles.textInput, { color: colors.textPrimary }]}
            />
            <Pressable
              onPress={() => setShowPassword(!showPassword)}
              style={styles.eyeBtn}
              hitSlop={10}
            >
              <SymbolView
                name={showPassword ? 'eye.slash.fill' : 'eye.fill'}
                size={18}
                tintColor={colors.icon}
              />
            </Pressable>
          </View>
        </View>

        {error && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{error}</Text>
          </View>
        )}

        {/* Native Action Button */}
        <IOSNativeButton
          title={canCancel ? t.connectAccount : t.logIn}
          onPress={handleLogin}
          isLoading={isLoading}
          style={styles.submitBtn}
        />
      </ScrollView>

      <PrivacyModal
        visible={privacyVisible}
        onClose={() => setPrivacyVisible(false)}
      />
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 40,
  },
  topNavRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
    minHeight: 36,
  },
  backBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    marginLeft: -6,
    gap: 4,
  },
  backText: {
    fontSize: 17,
    fontWeight: '400',
    letterSpacing: -0.3,
  },
  navPlaceholder: {
    width: 36,
    height: 36,
  },
  infoCircleBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    justifyContent: 'center',
    alignItems: 'center',
  },
  titleContainer: {
    marginBottom: 24,
    paddingHorizontal: 4,
  },
  screenTitle: {
    fontSize: 34,
    fontWeight: '700',
    letterSpacing: -0.6,
    marginBottom: 6,
  },
  screenSubtitle: {
    fontSize: 15,
    fontWeight: '400',
    lineHeight: 20,
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: '400',
    letterSpacing: -0.08,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  segmentedControl: {
    height: 36,
    marginBottom: 24,
  },
  groupedCard: {
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 24,
  },
  formRow: {
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  inputLeadingIcon: {
    marginRight: 12,
  },
  textInput: {
    flex: 1,
    fontSize: 16,
  },
  eyeBtn: {
    padding: 6,
  },
  rowDivider: {
    height: StyleSheet.hairlineWidth,
    marginLeft: 46,
  },
  errorBanner: {
    padding: 14,
    borderRadius: 12,
    backgroundColor: 'rgba(255, 69, 58, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255, 69, 58, 0.3)',
    marginBottom: 16,
  },
  errorBannerText: {
    color: '#FF453A',
    fontSize: 14,
    lineHeight: 18,
  },
  submitBtn: {
    marginTop: 4,
  },
});
