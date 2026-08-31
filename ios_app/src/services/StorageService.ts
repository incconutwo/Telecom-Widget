import * as SecureStore from 'expo-secure-store';
import { SavedAccount, ConsumptionData } from '../types';

const ACCOUNTS_KEY = 'telecom_saved_accounts';
const ACTIVE_ACCOUNT_ID_KEY = 'telecom_active_account_id';
const BIOMETRICS_ENABLED_KEY = 'telecom_biometrics_enabled';

export class StorageService {
  public static async getAccounts(): Promise<SavedAccount[]> {
    try {
      const json = await SecureStore.getItemAsync(ACCOUNTS_KEY);
      if (!json) return [];
      return JSON.parse(json);
    } catch (_e) {
      return [];
    }
  }

  public static async saveAccounts(accounts: SavedAccount[]): Promise<void> {
    await SecureStore.setItemAsync(ACCOUNTS_KEY, JSON.stringify(accounts));
  }

  public static async getActiveAccountId(): Promise<string | null> {
    try {
      return await SecureStore.getItemAsync(ACTIVE_ACCOUNT_ID_KEY);
    } catch (_e) {
      return null;
    }
  }

  public static async setActiveAccountId(id: string): Promise<void> {
    await SecureStore.setItemAsync(ACTIVE_ACCOUNT_ID_KEY, id);
  }

  public static async getBiometricsEnabled(): Promise<boolean> {
    try {
      const val = await SecureStore.getItemAsync(BIOMETRICS_ENABLED_KEY);
      return val === 'true';
    } catch (_e) {
      return false;
    }
  }

  public static async setBiometricsEnabled(enabled: boolean): Promise<void> {
    await SecureStore.setItemAsync(BIOMETRICS_ENABLED_KEY, enabled ? 'true' : 'false');
  }

  public static async updateAccountData(accountId: string, data: ConsumptionData, cookies: string[]): Promise<SavedAccount[]> {
    const accounts = await this.getAccounts();
    const updated = accounts.map((acc) => {
      if (acc.id === accountId) {
        return {
          ...acc,
          cachedData: data,
          cookies: cookies.length > 0 ? cookies : acc.cookies,
          lastUpdated: Date.now(),
        };
      }
      return acc;
    });
    await this.saveAccounts(updated);
    return updated;
  }
}
