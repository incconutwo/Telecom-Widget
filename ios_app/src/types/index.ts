export interface ConsumptionDetail {
  label: string;
  value: string;
  iconType: 'internet' | 'calls' | 'sms' | 'solde' | 'wallet' | 'orange' | 'global' | string;
}

export interface ConsumptionData {
  operator: string;
  phoneNumber: string;
  callsRemaining: string;
  callsPercent?: number | null;
  internetRemaining: string;
  internetPercent?: number | null;
  mainBalance?: string | null;
  extraDetails?: string | null;
  structuredDetails?: ConsumptionDetail[];
}

export interface SavedAccount {
  id: string;
  operator: string;
  email: string;
  password?: string;
  phone: string;
  cookies: string[];
  cachedData?: ConsumptionData | null;
  lastUpdated: number;
  liveNotificationEnabled: boolean;
}
