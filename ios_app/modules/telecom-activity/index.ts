import { requireNativeModule } from 'expo-modules-core';

export interface TelecomActivityModuleType {
  updateWidgetData(options: Record<string, any>): Promise<{ status: string }>;
  syncAllAccounts(options: { activeAccountId: string; accountsJson: string }): Promise<{ status: string }>;
  startOrUpdateActivity(options: Record<string, any>): Promise<{ status: string; id?: string }>;
  stopActivity(): Promise<{ status: string }>;
  isActivityActive(): Promise<boolean>;
}

let TelecomActivity: TelecomActivityModuleType | null = null;
try {
  TelecomActivity = requireNativeModule<TelecomActivityModuleType>('TelecomActivity');
} catch (e) {
  console.warn('TelecomActivity native module not loaded:', e);
}

export default TelecomActivity;
