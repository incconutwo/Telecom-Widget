import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  Switch,
  ActivityIndicator,
  Pressable,
  useColorScheme,
} from 'react-native';
import { SymbolView } from 'expo-symbols';
import * as Haptics from 'expo-haptics';
import { SavedAccount, ConsumptionData } from '../types';
import { MarocTelecomClient } from '../services/MarocTelecomClient';
import { OrangeClient } from '../services/OrangeClient';
import { InwiClient } from '../services/InwiClient';
import { LiveActivityService } from '../services/LiveActivityService';
import { t, isRTL } from '../utils/i18n';
import { formatPhoneNumber, formatCallsDisplay, formatBreakdownLabel } from '../utils/formatters';

interface DashboardScreenProps {
  account: SavedAccount;
  onOpenSettings: () => void;
  onAddNew: () => void;
  onAccountUpdated: (updated: SavedAccount) => void;
  onLogout: () => void;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  account,
  onOpenSettings,
  onAddNew,
  onAccountUpdated,
}) => {
  const [isRefreshing, setIsRefreshing] = useState(false);
  const colorScheme = useColorScheme();
  const isDark = colorScheme !== 'light';

  // Automatically sync widget data and accounts on mount and on account update
  React.useEffect(() => {
    if (account?.cachedData) {
      LiveActivityService.updateWidgetData(account);
      LiveActivityService.syncAllAccounts([account], account.id);
    }
  }, [account]);

  // Carrier-Specific Brand Accents
  const getCarrierTheme = (operator: string) => {
    switch (operator) {
      case 'Orange':
        return {
          brandColor: '#FF7900',
          badgeBg: isDark ? 'rgba(255, 121, 0, 0.22)' : 'rgba(255, 121, 0, 0.14)',
          shortName: 'Orange',
        };
      case 'Inwi':
        return {
          brandColor: isDark ? '#D6006E' : '#A01A7D',
          badgeBg: isDark ? 'rgba(214, 0, 110, 0.22)' : 'rgba(160, 26, 125, 0.14)',
          shortName: 'Inwi',
        };
      case 'Maroc Telecom':
      default:
        return {
          brandColor: isDark ? '#0A84FF' : '#007AFF',
          badgeBg: isDark ? 'rgba(10, 132, 255, 0.22)' : 'rgba(0, 122, 255, 0.14)',
          shortName: 'IAM',
        };
    }
  };

  const carrierTheme = getCarrierTheme(account.operator);

  // Apple HIG Semantic Colors
  const colors = {
    background: isDark ? '#000000' : '#F2F2F7',
    card: isDark ? '#1C1C1E' : '#FFFFFF',
    textPrimary: isDark ? '#FFFFFF' : '#000000',
    textSecondary: isDark ? 'rgba(235, 235, 245, 0.6)' : 'rgba(60, 60, 67, 0.6)',
    divider: isDark ? 'rgba(255, 255, 255, 0.12)' : 'rgba(60, 60, 67, 0.12)',
    trackBg: isDark ? 'rgba(255, 255, 255, 0.10)' : 'rgba(0, 0, 0, 0.06)',
    navBlue: isDark ? '#0A84FF' : '#007AFF',
    green: isDark ? '#30D158' : '#34C759',
    orange: isDark ? '#FF9F0A' : '#FF9500',
    purple: isDark ? '#BF5AF2' : '#AF52DE',
  };

  const data = account.cachedData;

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);

    try {
      let freshData: ConsumptionData;
      let freshCookies: string[] = [];

      if (account.operator === 'Maroc Telecom') {
        const client = new MarocTelecomClient(
          account.email,
          account.password || '',
          account.phone,
          account.cookies
        );
        freshData = await client.fetchConsumption();
        freshCookies = client.currentCookies;
      } else if (account.operator === 'Orange') {
        const client = new OrangeClient(
          account.email,
          account.password || '',
          account.phone || account.email,
          account.cookies
        );
        freshData = await client.fetchConsumption();
        freshCookies = client.currentCookies;
      } else {
        const client = new InwiClient(
          account.phone || account.email,
          account.password || '',
          account.phone || account.email,
          account.cookies
        );
        freshData = await client.fetchConsumption();
        freshCookies = client.currentCookies;
      }

      const updated: SavedAccount = {
        ...account,
        cachedData: freshData,
        cookies: freshCookies.length > 0 ? freshCookies : account.cookies,
        lastUpdated: Date.now(),
      };

      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      onAccountUpdated(updated);

      if (updated.liveNotificationEnabled) {
        LiveActivityService.startOrUpdateLiveActivity(updated);
      }
    } catch (_e) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleToggleLiveActivity = (val: boolean) => {
    Haptics.selectionAsync();
    const updated: SavedAccount = {
      ...account,
      liveNotificationEnabled: val,
    };
    onAccountUpdated(updated);
    if (val) {
      LiveActivityService.startOrUpdateLiveActivity(updated);
    } else {
      LiveActivityService.stopLiveActivity();
    }
  };

  if (!data) {
    return (
      <View style={[styles.centerContainer, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={carrierTheme.brandColor} />
      </View>
    );
  }

  // Normalization logic for Internet (GB/MB)
  const internetProgress = (() => {
    if (data.internetPercent != null) {
      return Math.min(1, Math.max(0.04, data.internetPercent / 100));
    }
    const match = data.internetRemaining.match(/(\d+(?:[.,]\d+)?)/);
    if (!match) return 0.04;
    const num = parseFloat(match[1].replace(',', '.'));
    if (num <= 0) return 0.04;

    const lower = data.internetRemaining.toLowerCase();
    if (lower.includes('mo') || lower.includes('mb')) {
      return Math.min(1, Math.max(0.04, num / 1000));
    }
    return Math.min(1, Math.max(0.04, num / 25)); // Base cap 25 GB scale
  })();

  // Normalization logic for Calls (Hours/Minutes)
  const callsProgress = (() => {
    if (data.callsPercent != null) {
      return Math.min(1, Math.max(0.04, data.callsPercent / 100));
    }
    const match = data.callsRemaining.match(/(\d+)/);
    if (!match) return 0.04;
    const num = parseFloat(match[1]);
    if (num <= 0) return 0.04;

    const lower = data.callsRemaining.toLowerCase();
    if (lower.includes('h')) {
      return Math.min(1, Math.max(0.04, num / 10)); // Base cap 10 Hours
    }
    return Math.min(1, Math.max(0.04, num / 120)); // Base cap 120 Minutes
  })();

  const internetPercentText =
    data.internetPercent != null ? `${Math.round(data.internetPercent)}%` : null;
  const callsPercentText =
    data.callsPercent != null ? `${Math.round(data.callsPercent)}%` : null;

  // Category Icon Mapper for Structured Line Items
  const getBreakdownIcon = (type: string) => {
    switch (type.toLowerCase()) {
      case 'wallet':
      case 'solde':
        return { name: 'creditcard.fill', color: colors.orange };
      case 'sms':
        return { name: 'message.fill', color: colors.purple };
      case 'calls':
      case 'orange':
        return { name: 'phone.fill', color: colors.green };
      case 'global':
        return { name: 'globe.americas.fill', color: colors.navBlue };
      default:
        return { name: 'antenna.radiowaves.left.and.right', color: carrierTheme.brandColor };
    }
  };

  const hasExtraCredit = Boolean(data.extraDetails && data.extraDetails.trim() !== '' && data.extraDetails !== '0.00 Dh');

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Top Header */}
      <View style={styles.topBar}>
        <View style={styles.topBarLeft}>
          <Pressable
            onPress={() => {
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
              onOpenSettings();
            }}
            style={styles.iconBtn}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={t.settings}
          >
            <SymbolView name="gearshape.fill" size={22} tintColor={colors.navBlue} />
          </Pressable>
          <View>
            <View style={styles.carrierRow}>
              <Text style={[styles.topBarTitle, { color: colors.textPrimary }]}>
                {data.operator}
              </Text>
              <View style={[styles.operatorBadge, { backgroundColor: carrierTheme.badgeBg }]}>
                <Text style={[styles.operatorBadgeText, { color: carrierTheme.brandColor }]}>
                  {carrierTheme.shortName}
                </Text>
              </View>
            </View>
            <Text style={[styles.topBarSubtitle, { color: colors.textSecondary }]}>
              {formatPhoneNumber(data.phoneNumber)}
            </Text>
          </View>
        </View>

        <View style={styles.topBarActions}>
          <Pressable
            onPress={() => {
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
              onAddNew();
            }}
            style={styles.iconBtn}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={t.addAccount}
          >
            <SymbolView name="plus" size={22} tintColor={colors.navBlue} />
          </Pressable>

          <Pressable
            onPress={handleRefresh}
            disabled={isRefreshing}
            style={styles.iconBtn}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={t.refresh}
          >
            {isRefreshing ? (
              <ActivityIndicator size="small" color={colors.navBlue} />
            ) : (
              <SymbolView name="arrow.clockwise" size={20} tintColor={colors.navBlue} />
            )}
          </Pressable>
        </View>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={handleRefresh}
            tintColor={colors.navBlue}
          />
        }
      >
        {/* Section 1: Main Metric Tiles */}
        <Text style={[styles.sectionHeader, { color: colors.textSecondary }]}>{t.balance}</Text>
        
        {/* Optional Solde Principal Card (for Orange & Inwi prepaid/recharge balances) */}
        {hasExtraCredit && (
          <View style={[styles.soldeTile, { backgroundColor: colors.card, borderColor: colors.divider }]}>
            <View style={styles.tileHeader}>
              <View style={[styles.tileIconBadge, { backgroundColor: `${colors.orange}20` }]}>
                <SymbolView name="creditcard.fill" size={16} tintColor={colors.orange} />
              </View>
              <Text style={[styles.tileLabel, { color: colors.textSecondary }]}>{t.mainBalance}</Text>
            </View>
            <Text style={[styles.soldeValue, { color: colors.textPrimary }]}>
              {data.extraDetails}
            </Text>
          </View>
        )}

        {/* 2-Column Grid (Internet & Calls) */}
        <View style={styles.metricGrid}>
          {/* Internet Tile */}
          <View style={[styles.metricTile, { backgroundColor: colors.card }]}>
            <View style={styles.tileHeader}>
              <View style={styles.tileHeaderLeft}>
                <View style={[styles.tileIconBadge, { backgroundColor: carrierTheme.badgeBg }]}>
                  <SymbolView name="globe" size={15} tintColor={carrierTheme.brandColor} />
                </View>
                <Text style={[styles.tileLabel, { color: colors.textSecondary }]}>{t.internet}</Text>
              </View>
              {internetPercentText && (
                <Text style={[styles.tilePercentText, { color: colors.textSecondary }]}>
                  {internetPercentText}
                </Text>
              )}
            </View>
            <Text
              style={[styles.tileValue, { color: colors.textPrimary }]}
              numberOfLines={1}
              adjustsFontSizeToFit={true}
              minimumFontScale={0.75}
            >
              {data.internetRemaining}
            </Text>
            <View style={[styles.progressTrack, { backgroundColor: colors.trackBg }]}>
              <View
                style={[
                  styles.progressFill,
                  { width: `${internetProgress * 100}%`, backgroundColor: carrierTheme.brandColor },
                ]}
              />
            </View>
          </View>

          {/* Voice Calls Tile */}
          <View style={[styles.metricTile, { backgroundColor: colors.card }]}>
            <View style={styles.tileHeader}>
              <View style={styles.tileHeaderLeft}>
                <View style={[styles.tileIconBadge, { backgroundColor: `${colors.green}20` }]}>
                  <SymbolView name="phone.fill" size={15} tintColor={colors.green} />
                </View>
                <Text style={[styles.tileLabel, { color: colors.textSecondary }]}>{t.calls}</Text>
              </View>
              {callsPercentText && (
                <Text style={[styles.tilePercentText, { color: colors.textSecondary }]}>
                  {callsPercentText}
                </Text>
              )}
            </View>
            <Text
              style={[styles.tileValue, { color: colors.textPrimary }]}
              numberOfLines={1}
              adjustsFontSizeToFit={true}
              minimumFontScale={0.65}
            >
              {formatCallsDisplay(data.callsRemaining)}
            </Text>
            <View style={[styles.progressTrack, { backgroundColor: colors.trackBg }]}>
              <View
                style={[
                  styles.progressFill,
                  { width: `${callsProgress * 100}%`, backgroundColor: colors.green },
                ]}
              />
            </View>
          </View>
        </View>

        {/* Section 2: Live Activity Inset-Grouped Row */}
        <Text style={[styles.sectionHeader, { color: colors.textSecondary }]}>{t.liveActivities}</Text>
        <View style={[styles.groupedCard, { backgroundColor: colors.card }]}>
          <View style={styles.row}>
            <View style={[styles.rowIconBadge, { backgroundColor: colors.orange }]}>
              <SymbolView name="bell.badge.fill" size={16} tintColor="#FFFFFF" />
            </View>
            <View style={styles.rowTextCol}>
              <Text style={[styles.rowTitle, { color: colors.textPrimary }]}>{t.liveStatusTitle}</Text>
              <Text style={[styles.rowSubtitle, { color: colors.textSecondary }]}>
                {t.liveStatusSubtitle}
              </Text>
            </View>
            <Switch
              value={!!account.liveNotificationEnabled}
              onValueChange={handleToggleLiveActivity}
            />
          </View>
        </View>

        {/* Section 3: Plan Breakdown (Rich parsed details for Orange & Inwi) */}
        {data.structuredDetails && data.structuredDetails.length > 0 && (
          <>
            <Text style={[styles.sectionHeader, { color: colors.textSecondary }]}>
              {t.planDetails}
            </Text>
            <View style={[styles.groupedCard, { backgroundColor: colors.card }]}>
              {data.structuredDetails.map((detail, index) => {
                const iconInfo = getBreakdownIcon(detail.iconType);
                const isLast = index === data.structuredDetails!.length - 1;

                return (
                  <View key={`${detail.label}_${index}`}>
                    <View style={styles.breakdownRow}>
                      <View style={[styles.rowIconBadge, { backgroundColor: iconInfo.color }]}>
                        <SymbolView name={iconInfo.name as any} size={15} tintColor="#FFFFFF" />
                      </View>
                      <Text style={[styles.breakdownLabel, { color: colors.textPrimary }]}>
                        {formatBreakdownLabel(detail.label)}
                      </Text>
                      <Text style={[styles.breakdownValue, { color: colors.textSecondary }]}>
                        {detail.value}
                      </Text>
                    </View>
                    {!isLast && (
                      <View style={[styles.hairlineDivider, { backgroundColor: colors.divider }]} />
                    )}
                  </View>
                );
              })}
            </View>
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 16,
  },
  topBarLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  carrierRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  topBarTitle: {
    fontSize: 20,
    fontWeight: '700',
    letterSpacing: -0.4,
  },
  operatorBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  operatorBadgeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  topBarSubtitle: {
    fontSize: 13,
    fontWeight: '400',
    marginTop: 1,
  },
  topBarActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  iconBtn: {
    padding: 6,
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 60,
  },
  sectionHeader: {
    fontSize: 13,
    fontWeight: '400',
    letterSpacing: -0.08,
    textTransform: 'uppercase',
    paddingHorizontal: 16,
    marginBottom: 8,
    marginTop: 18,
  },
  soldeTile: {
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  soldeValue: {
    fontSize: 26,
    fontWeight: '700',
    letterSpacing: -0.4,
    marginTop: 8,
  },
  metricGrid: {
    flexDirection: 'row',
    gap: 10,
  },
  metricTile: {
    flex: 1,
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 14,
    justifyContent: 'space-between',
    minHeight: 108,
  },
  tileHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  tileHeaderLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  tileIconBadge: {
    width: 26,
    height: 26,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  tileLabel: {
    fontSize: 13,
    fontWeight: '500',
  },
  tilePercentText: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: -0.1,
  },
  tileValue: {
    fontSize: 20,
    fontWeight: '700',
    letterSpacing: -0.3,
    marginVertical: 10,
  },
  progressTrack: {
    height: 5,
    borderRadius: 2.5,
    overflow: 'hidden',
    width: '100%',
  },
  progressFill: {
    height: '100%',
    borderRadius: 2.5,
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
  rowIconBadge: {
    width: 28,
    height: 28,
    borderRadius: 7,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  rowTextCol: {
    flex: 1,
    marginRight: 10,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '400',
    letterSpacing: -0.2,
  },
  rowSubtitle: {
    fontSize: 12,
    marginTop: 1,
  },
  breakdownRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  breakdownLabel: {
    flex: 1,
    fontSize: 16,
    fontWeight: '400',
  },
  breakdownValue: {
    fontSize: 16,
    fontWeight: '400',
  },
  hairlineDivider: {
    height: StyleSheet.hairlineWidth,
    marginLeft: 56,
  },
});