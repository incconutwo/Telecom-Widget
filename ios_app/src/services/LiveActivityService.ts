import { NativeModules, Platform } from 'react-native';
import { SavedAccount } from '../types';
import { formatPhoneNumber, formatCallsDisplay } from '../utils/formatters';
import { t } from '../utils/i18n';

const { TelecomActivityModule } = NativeModules;

export class LiveActivityService {
  private static buildPayload(account: SavedAccount) {
    const data = account.cachedData;
    if (!data) return null;

    // Normalization logic for Internet (GB/MB)
    const internetPercent = (() => {
      if (data.internetPercent != null) return data.internetPercent;
      const match = data.internetRemaining.match(/(\d+(?:[.,]\d+)?)/);
      if (!match) return 50.0;
      const num = parseFloat(match[1].replace(',', '.'));
      if (num <= 0) return 0.0;
      const lower = data.internetRemaining.toLowerCase();
      if (lower.includes('mo') || lower.includes('mb')) {
        return Math.min(100, Math.max(4, (num / 1000) * 100));
      }
      return Math.min(100, Math.max(4, (num / 25) * 100));
    })();

    // Normalization logic for Calls (Hours/Minutes)
    const callsPercent = (() => {
      if (data.callsPercent != null) return data.callsPercent;
      const match = data.callsRemaining.match(/(\d+)/);
      if (!match) return 50.0;
      const num = parseFloat(match[1]);
      if (num <= 0) return 0.0;
      const lower = data.callsRemaining.toLowerCase();
      if (lower.includes('h')) {
        return Math.min(100, Math.max(4, (num / 10) * 100));
      }
      return Math.min(100, Math.max(4, (num / 120) * 100));
    })();

    return {
      accountId: account.id,
      operator: account.operator,
      phoneNumber: formatPhoneNumber(account.phone || data.phoneNumber),
      internetRemaining: data.internetRemaining,
      internetPercent: internetPercent,
      internetLabel: t.internet,
      callsRemaining: formatCallsDisplay(data.callsRemaining),
      callsPercent: callsPercent,
      callsLabel: t.calls,
      mainBalance:
        data.mainBalance ||
        data.structuredDetails?.find(
          (d) => d.iconType === 'solde' || d.label.toLowerCase().includes('solde')
        )?.value ||
        '',
      timestamp: Date.now(),
    };
  }

  public static async updateWidgetData(account: SavedAccount): Promise<void> {
    if (Platform.OS !== 'ios' || !account.cachedData) return;
    const payload = this.buildPayload(account);
    if (!payload) return;

    if (TelecomActivityModule?.updateWidgetData) {
      try {
        await TelecomActivityModule.updateWidgetData(payload);
      } catch (e) {
        console.warn('Home Screen Widget updateWidgetData error:', e);
      }
    }
  }

  public static async syncAllAccounts(
    accounts: SavedAccount[],
    activeAccountId: string | null
  ): Promise<void> {
    if (Platform.OS !== 'ios' || accounts.length === 0) return;

    const accountsData = accounts.map((acc) => {
      const payload = this.buildPayload(acc);
      return {
        id: acc.id,
        operatorName: acc.operator,
        phone: formatPhoneNumber(acc.phone || acc.cachedData?.phoneNumber || ''),
        internetRemaining: payload?.internetRemaining || '0 Go',
        internetPercent: payload?.internetPercent ?? 0.0,
        internetLabel: t.internet,
        callsRemaining: payload?.callsRemaining || '0h 00m',
        callsPercent: payload?.callsPercent ?? 0.0,
        callsLabel: t.calls,
        mainBalance: payload?.mainBalance || '',
        timestamp: acc.lastUpdated || Date.now(),
      };
    });

    const active = accounts.find((a) => a.id === activeAccountId) || accounts[0];
    if (active) {
      await this.updateWidgetData(active);
    }

    if (TelecomActivityModule?.syncAllAccounts) {
      try {
        await TelecomActivityModule.syncAllAccounts({
          activeAccountId: activeAccountId || accounts[0]?.id || '',
          accountsJson: JSON.stringify(accountsData),
        });
      } catch (e) {
        console.warn('Home Screen Widget syncAllAccounts error:', e);
      }
    }
  }

  public static async startOrUpdateLiveActivity(account: SavedAccount): Promise<void> {
    if (Platform.OS !== 'ios' || !account.cachedData) return;
    const payload = this.buildPayload(account);
    if (!payload) return;

    // Always keep Home Screen widgets updated
    await this.updateWidgetData(account);

    if (TelecomActivityModule?.startOrUpdateActivity) {
      try {
        await TelecomActivityModule.startOrUpdateActivity(payload);
      } catch (e) {
        console.warn('Live Activity startOrUpdateActivity error:', e);
      }
    } else if (TelecomActivityModule?.updateActivity) {
      try {
        await TelecomActivityModule.updateActivity(payload);
      } catch (e) {
        console.warn('Live Activity updateActivity error:', e);
      }
    }
  }

  public static async stopLiveActivity(): Promise<void> {
    if (Platform.OS !== 'ios') return;
    if (TelecomActivityModule?.stopActivity) {
      try {
        await TelecomActivityModule.stopActivity();
      } catch (e) {
        console.warn('Live Activity stop error:', e);
      }
    }
  }
}
