import { getLocales } from 'expo-localization';
import { I18nManager } from 'react-native';

export type LanguageCode = 'en' | 'fr' | 'ar' | 'zgh';

export interface Translations {
  // Common
  appName: string;
  cancel: string;
  done: string;
  continue: string;
  add: string;
  active: string;
  remove: string;

  // Dashboard
  balance: string;
  internet: string;
  calls: string;
  mainBalance: string;
  liveActivities: string;
  liveStatusTitle: string;
  liveStatusSubtitle: string;
  planDetails: string;
  settings: string;
  addAccount: string;
  refresh: string;

  // Login
  welcome: string;
  welcomeSubtitle: string;
  addAccountTitle: string;
  addAccountSubtitle: string;
  operator: string;
  credentials: string;
  email: string;
  emailOrPhone: string;
  phone: string;
  password: string;
  logIn: string;
  connectAccount: string;
  privacyAndSecurity: string;
  errorEmptyCredentials: string;
  errorEmptyPassword: string;
  loginFailed: string;

  // Settings & Security
  accounts: string;
  security: string;
  faceId: string;
  touchId: string;
  passcode: string;
  biometricsSubtitle: string;
  unlockApp: string;
  unlockPrompt: string;
  appLocked: string;
  authenticateToUnlock: string;
  about: string;
  privacySubtitle: string;
  appVersion: string;
  removeAccountTitle: string;
  removeAccountMessage: (operator: string, identifier: string) => string;

  // Privacy Modal
  privacyHeroTitle: string;
  privacyHeroSubtitle: string;
  keychainTitle: string;
  keychainDesc: string;
  noIntermediaryTitle: string;
  noIntermediaryDesc: string;
  openSourceTitle: string;
  openSourceDesc: string;
}

const en: Translations = {
  appName: 'Telecom Widget',
  cancel: 'Cancel',
  done: 'Done',
  continue: 'Continue',
  add: 'Add',
  active: 'Active',
  remove: 'Remove Account',

  balance: 'BALANCE',
  internet: 'Internet',
  calls: 'Calls',
  mainBalance: 'Main Balance',
  liveActivities: 'LIVE ACTIVITIES',
  liveStatusTitle: 'Live Balance Status',
  liveStatusSubtitle: 'Show on Lock Screen and in Dynamic Island',
  planDetails: 'PLAN DETAILS',
  settings: 'Settings',
  addAccount: 'Add Account',
  refresh: 'Refresh',

  welcome: 'Welcome',
  welcomeSubtitle: 'Sign in to monitor your cellular and internet usage',
  addAccountTitle: 'Add Account',
  addAccountSubtitle: 'Connect an additional telecom subscription',
  operator: 'OPERATOR',
  credentials: 'CREDENTIALS',
  email: 'Email',
  emailOrPhone: 'Phone Number or Email',
  phone: 'Phone Number',
  password: 'Password',
  logIn: 'Log In',
  connectAccount: 'Connect Account',
  privacyAndSecurity: 'Privacy & Security',
  errorEmptyCredentials: 'Please enter your email or phone number',
  errorEmptyPassword: 'Please enter your password',
  loginFailed: 'Login failed. Please check your credentials.',

  accounts: 'ACCOUNTS',
  security: 'SECURITY',
  faceId: 'Face ID & Passcode',
  touchId: 'Touch ID & Passcode',
  passcode: 'Passcode',
  biometricsSubtitle: 'Require biometric unlock on launch',
  unlockApp: 'Unlock with Face ID',
  unlockPrompt: 'Authenticate to access Telecom Widget',
  appLocked: 'Telecom Widget is Locked',
  authenticateToUnlock: 'Tap to authenticate and view your subscriptions',
  about: 'ABOUT',
  privacySubtitle: 'Keychain & on-device encryption',
  appVersion: 'App Version',
  removeAccountTitle: 'Remove Account',
  removeAccountMessage: (op, id) => `Are you sure you want to remove ${op} (${id})?`,

  privacyHeroTitle: 'Privacy & Security',
  privacyHeroSubtitle: 'How Telecom Widget protects your credentials and personal data.',
  keychainTitle: 'Hardware Keychain Storage',
  keychainDesc: 'Your phone numbers and passwords are encrypted strictly on-device using Apple Secure Enclave & iOS Keychain.',
  noIntermediaryTitle: 'Zero Intermediary Servers',
  noIntermediaryDesc: 'All telemetry data and balance requests communicate directly from your iPhone to the official telecom carrier endpoints.',
  openSourceTitle: 'Independent & Open Source',
  openSourceDesc: 'This application is an independent client utility and is not affiliated with, sponsored by, or endorsed by Maroc Telecom, Orange, or Inwi.',
};

const fr: Translations = {
  appName: 'Telecom Widget',
  cancel: 'Annuler',
  done: 'OK',
  continue: 'Continuer',
  add: 'Ajouter',
  active: 'Actif',
  remove: 'Supprimer le compte',

  balance: 'SOLDE',
  internet: 'Internet',
  calls: 'Appels',
  mainBalance: 'Solde Principal',
  liveActivities: 'ACTIVITÉS EN DIRECT',
  liveStatusTitle: 'Statut du solde en direct',
  liveStatusSubtitle: 'Écran verrouillé et Dynamic Island',
  planDetails: 'FORFAIT',
  settings: 'Réglages',
  addAccount: 'Ajouter un compte',
  refresh: 'Actualiser',

  welcome: 'Bienvenue',
  welcomeSubtitle: 'Suivez votre consommation mobile et internet',
  addAccountTitle: 'Ajouter un compte',
  addAccountSubtitle: 'Associez un forfait supplémentaire',
  operator: 'OPÉRATEUR',
  credentials: 'IDENTIFIANTS',
  email: 'E-mail',
  emailOrPhone: 'Numéro ou e-mail',
  phone: 'Numéro de téléphone',
  password: 'Mot de passe',
  logIn: 'Se connecter',
  connectAccount: 'Associer',
  privacyAndSecurity: 'Confidentialité et sécurité',
  errorEmptyCredentials: 'Veuillez saisir votre e-mail ou numéro',
  errorEmptyPassword: 'Veuillez saisir votre mot de passe',
  loginFailed: 'Échec de connexion. Vérifiez vos identifiants.',

  accounts: 'COMPTES',
  security: 'SÉCURITÉ',
  faceId: 'Face ID et code',
  touchId: 'Touch ID et code',
  passcode: 'Code d’accès',
  biometricsSubtitle: 'Verrouiller l’application à l’ouverture',
  unlockApp: 'Déverrouiller avec Face ID',
  unlockPrompt: 'Authentifiez-vous pour accéder à Telecom Widget',
  appLocked: 'Application verrouillée',
  authenticateToUnlock: 'Touchez pour vous authentifier',
  about: 'À PROPOS',
  privacySubtitle: 'Chiffrement sécurisé sur l’appareil',
  appVersion: 'Version de l’app',
  removeAccountTitle: 'Supprimer le compte',
  removeAccountMessage: (op, id) => `Voulez-vous supprimer ${op} (${id}) ?`,

  privacyHeroTitle: 'Confidentialité et sécurité',
  privacyHeroSubtitle: 'Protection de vos identifiants et de vos données.',
  keychainTitle: 'Stockage Trousseau Sécurisé',
  keychainDesc: 'Identifiants chiffrés sur l’appareil avec l’Apple Secure Enclave et le Trousseau iOS.',
  noIntermediaryTitle: 'Aucun serveur intermédiaire',
  noIntermediaryDesc: 'Requêtes de solde directes entre votre iPhone et les serveurs officiels opérateurs.',
  openSourceTitle: 'Indépendant et Open Source',
  openSourceDesc: 'Utilitaire client indépendant, non affilié à Maroc Telecom, Orange ou Inwi.',
};

const ar: Translations = {
  appName: 'Telecom Widget',
  cancel: 'إلغاء',
  done: 'تم',
  continue: 'متابعة',
  add: 'إضافة',
  active: 'نشط',
  remove: 'حذف الحساب',

  balance: 'الرصيد',
  internet: 'الإنترنت',
  calls: 'المكالمات',
  mainBalance: 'الرصيد الرئيسي',
  liveActivities: 'الأنشطة المباشرة',
  liveStatusTitle: 'حالة الرصيد المباشرة',
  liveStatusSubtitle: 'شاشة القفل والجزيرة التفاعلية',
  planDetails: 'الباقة',
  settings: 'الإعدادات',
  addAccount: 'إضافة حساب',
  refresh: 'تحديث',

  welcome: 'مرحباً',
  welcomeSubtitle: 'تابع استهلاك الرصيد والإنترنت بسهولة',
  addAccountTitle: 'إضافة حساب',
  addAccountSubtitle: 'ربط اشتراك هاتفي إضافي',
  operator: 'المشغّل',
  credentials: 'بيانات الدخول',
  email: 'البريد الإلكتروني',
  emailOrPhone: 'الهاتف أو البريد',
  phone: 'رقم الهاتف',
  password: 'كلمة المرور',
  logIn: 'تسجيل الدخول',
  connectAccount: 'ربط الحساب',
  privacyAndSecurity: 'الخصوصية والأمان',
  errorEmptyCredentials: 'يرجى إدخال البريد الإلكتروني أو الهاتف',
  errorEmptyPassword: 'يرجى إدخال كلمة المرور',
  loginFailed: 'فشل تسجيل الدخول. يرجى التحقق من البيانات.',

  accounts: 'الحسابات',
  security: 'الأمان',
  faceId: 'بصمة الوجه ورمز الدخول',
  touchId: 'بصمة الإصبع ورمز الدخول',
  passcode: 'رمز الدخول',
  biometricsSubtitle: 'طلب المصادقة عند فتح التطبيق',
  unlockApp: 'فتح القفل',
  unlockPrompt: 'المصادقة للوصول إلى التطبيق',
  appLocked: 'التطبيق مقفل',
  authenticateToUnlock: 'اضغط للمصادقة وعرض اشتراكاتك',
  about: 'حول',
  privacySubtitle: 'تشفير آمن محلياً على الجهاز',
  appVersion: 'إصدار التطبيق',
  removeAccountTitle: 'حذف الحساب',
  removeAccountMessage: (op, id) => `هل أنت متأكد من حذف ${op} (${id})؟`,

  privacyHeroTitle: 'الخصوصية والأمان',
  privacyHeroSubtitle: 'كيف يحمي التطبيق بياناتك واعتماداتك.',
  keychainTitle: 'تخزين مشفر في سلسلة المفاتيح',
  keychainDesc: 'تشفير آمن للبيانات محلياً على جهازك بواسطة Secure Enclave وسلسلة مفاتيح iOS.',
  noIntermediaryTitle: 'دون أي خوادم وسيطة',
  noIntermediaryDesc: 'اتصال مباشر من جهازك إلى الخوادم الرسمية لشركات الاتصالات دون وسيط.',
  openSourceTitle: 'مستقل ومفتوح المصدر',
  openSourceDesc: 'تطبيق مستقل مفتوح المصدر غير تابع رسمياً لاتصالات المغرب أو أورنج أو إنوي.',
};

const zgh: Translations = {
  appName: 'Telecom Widget',
  cancel: 'ⵙⵔﺡ',
  done: 'ⵉⵎⴷⴰ',
  continue: 'ⴹⴼⵕ',
  add: 'ⵔⵏⵓ',
  active: 'ⵉⵔⵎⴰⵏ',
  remove: 'ⴽⴽⵙ ⴰⵎⵉⴹⴰⵏ',

  balance: 'ⴰⵙⵉⴹⵏ',
  internet: 'ⵉⵏⵜⵉⵔⵏⵉⵜ',
  calls: 'ⵜⵉⵖⵔⵉⵡⵉⵏ',
  mainBalance: 'ⴰⵙⵉⴹⵏ ⴰⵎⵇⵔⴰⵏ',
  liveActivities: 'ⵉⵎⵓⵙⵙⵓⵜⵏ ⵉⵎⵉⵔⴰⵏⵏ',
  liveStatusTitle: 'ⴰⴷⴷⴰⴷ ⵏ ⵓⵙⵉⴹⵏ ⵉⵎⵉⵔⴰⵡ',
  liveStatusSubtitle: 'ⴰⴳⴷⵉⵍ ⵏ ⵓⵔⴳⴰⵢ ⴷ Dynamic Island',
  planDetails: 'ⵜⴰⵙⴷⴷⴰⵔⵜ',
  settings: 'ⵉⵙⵖⴰⵍⵏ',
  addAccount: 'ⵔⵏⵓ ⴰⵎⵉⴹⴰⵏ',
  refresh: 'ⵙⵎⴰⵢⵏⵓ',

  welcome: 'ⴰⵏⵙⵓⴼ',
  welcomeSubtitle: 'ⴹⴼⵕ ⴰⵙⵖⵍ ⵏ ⵡⴰⵏⵜⵉⵔⵏⵉⵜ ⴷ ⵜⵖⵔⵉⵡⵉⵏ',
  addAccountTitle: 'ⵔⵏⵓ ⴰⵎⵉⴹⴰⵏ',
  addAccountSubtitle: 'ⵔⵏⵓ ⴰⵙⵙⴰⵖ ⵏ ⵜⵉⵍⵉⴼⵓⵏ ⵢⴰⴹⵏ',
  operator: 'ⴰⵎⵙⵙⵓⴷⵙ',
  credentials: 'ⵉⵎⵙⴽⴰⵔⵏ',
  email: 'ⵉⵎⴰⵢⵍ',
  emailOrPhone: 'ⵓⵟⵟⵓⵏ ⵏⵉⵖ ⵉⵎⴰⵢⵍ',
  phone: 'ⵓⵟⵟⵓⵏ ⵏ ⵜⵉⵍⵉⴼⵓⵏ',
  password: 'ⵜⴰⴳⵓⵔⵉ ⵏ ⵓⵣⵔⴰⵢ',
  logIn: 'ⴽⵛⵎ',
  connectAccount: 'ⵙⵎⵓⵏ ⴰⵎⵉⴹⴰⵏ',
  privacyAndSecurity: 'ⵜⵉⵏⴼⵔⵓⵜ ⴷ ⵜⵏⴼⵔⵉⵜ',
  errorEmptyCredentials: 'ⵙⴽⵛⵎ ⵉⵎⴰⵢⵍ ⵏⵉⵖ ⵓⵟⵟⵓⵏ ⵏ ⵜⵉⵍⵉⴼⵓⵏ',
  errorEmptyPassword: 'ⵙⴽⵛⵎ ⵜⴰⴳⵓⵔⵉ ⵏ ⵓⵣⵔⴰⵢ',
  loginFailed: 'ⴰⵣⴳⴰⵍ ⴳ ⵓⴽⵛⴰⵎ. ⵙⵙⵏⵇⴷ ⵉⵙⴼⴽⴰ ⵏⵏⴽ.',

  accounts: 'ⵉⵎⵉⴹⴰⵏⵏ',
  security: 'ⵜⴰⵏⴼⵔⵓⵜ',
  faceId: 'Face ID & Passcode',
  touchId: 'Touch ID & Passcode',
  passcode: 'ⵜⴰⴳⵓⵔⵉ ⵏ ⵓⵣⵔⴰⵢ',
  biometricsSubtitle: 'ⵙⵙⵓⵜⵔ ⵜⴰⵏⴼⵔⵓⵜ ⴳ ⵓⵕⵥⵥⵓⵎ',
  unlockApp: 'ⵕⵥⵎ ⵙ Face ID',
  unlockPrompt: 'ⵙⵙⵏⵇⴷ ⵜⴰⵏⴼⵔⵓⵜ ⵏⵏⴽ',
  appLocked: 'ⴰⵙⵏⵙ ⵉⵇⵇⵏ',
  authenticateToUnlock: 'ⵜⵜⴽⵉ ⴰⴷ ⵜⵕⵥⵎⴷ ⵉⵎⵉⴹⴰⵏⵏ',
  about: 'ⵖⴼ',
  privacySubtitle: 'ⴰⵙⵙⵏⴼⵔ ⴰⵏⴳⵔⴰⵡ ⴳ ⵡⴰⵍⵍⴰⵍ',
  appVersion: 'ⵜⵓⵏⵖⵉⵍⵜ ⵏ ⵓⵙⵏⵙ',
  removeAccountTitle: 'ⴽⴽⵙ ⴰⵎⵉⴹⴰⵏ',
  removeAccountMessage: (op, id) => `ⵉⵙ ⵜⵔⵉⴷ ⴰⴷ ⵜⴽⴽⵙⴷ ${op} (${id})?`,

  privacyHeroTitle: 'ⵜⵉⵏⴼⵔⵓⵜ ⴷ ⵜⵏⴼⵔⵉⵜ',
  privacyHeroSubtitle: 'ⵎⴰⵏⵎⴽ ⵙ ⵉⵜⵜⵃⴹⵓ Telecom Widget ⵉⵙⴼⴽⴰ ⵏⵏⴽ.',
  keychainTitle: 'ⴰⵙⵎⴹⵍ ⴳ Keychain',
  keychainDesc: 'ⵜⵜⵓⴼⵔⴰⵏ ⵉⵎⵙⴽⴰⵔⵏ ⴷ ⵜⴳⵓⵔⵉⵡⵉⵏ ⵏ ⵓⵣⵔⴰⵢ ⴳ Apple Secure Enclave & iOS Keychain.',
  noIntermediaryTitle: 'ⵡⴰⵍⵓ ⵉⵎⵙⵙⵓⴷⴰⵙ ⵉⵏⴰⵎⵎⴰⵙⵏ',
  noIntermediaryDesc: 'ⴰⵣⴷⴰⵢ ⴰⵔ ⵉⵜⵜⵉⵍⵉ ⵙⵔⵉⴷ ⴳⵔ iPhone ⴷ ⵉⵎⵙⵙⵓⴷⴰⵙ ⵏ ⵜⵉⵍⵉⴽⵓⵎ.',
  openSourceTitle: 'ⵉⵍⴻⵍⵍⵉ ⴷ ⵓⵕⵥⵉⵎ',
  openSourceDesc: 'ⴰⵙⵏⵙ ⴰⴷ ⵉⴳⴰ ⴰⵍⴻⵍⵍⵉ, ⵓⵔ ⵉⵇⵇⵉⵏ ⵖⵔ Maroc Telecom, Orange ⵏⵉⵖ Inwi.',
};

const dictionaries: Record<LanguageCode, Translations> = { en, fr, ar, zgh };

export function getDeviceLanguage(): LanguageCode {
  try {
    const locales = getLocales();
    if (locales && locales.length > 0) {
      const code = locales[0].languageCode?.toLowerCase();
      if (code === 'fr') return 'fr';
      if (code === 'ar') return 'ar';
      if (code === 'zgh' || code === 'ber') return 'zgh';
    }
  } catch (_e) {}
  return 'en';
}

export const currentLanguage = getDeviceLanguage();
export const isRTL = currentLanguage === 'ar' || I18nManager.isRTL;
export const t = dictionaries[currentLanguage] || en;
