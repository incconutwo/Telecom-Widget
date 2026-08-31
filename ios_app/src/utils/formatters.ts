import { currentLanguage, LanguageCode } from './i18n';

/**
 * Formats raw Moroccan & international phone numbers into clean Apple HIG spaced groups.
 * Examples:
 *   "212610653694"  -> "+212 6 10 65 36 94"
 *   "0610653694"    -> "06 10 65 36 94"
 *   "0522123456"    -> "05 22 12 34 56"
 *   "212522123456"  -> "+212 5 22 12 34 56"
 */
export function formatPhoneNumber(raw?: string | null): string {
  if (!raw) return '';
  const cleaned = raw.trim().replace(/[^\d+]/g, '');

  // If international starting with +212 or 212
  if (cleaned.startsWith('+212') || cleaned.startsWith('212')) {
    const digits = cleaned.startsWith('+212') ? cleaned.slice(4) : cleaned.slice(3);
    if (digits.length === 9) {
      // e.g. 6 10 65 36 94 or 5 22 12 34 56
      const prefix = digits.slice(0, 1);
      const part1 = digits.slice(1, 3);
      const part2 = digits.slice(3, 5);
      const part3 = digits.slice(5, 7);
      const part4 = digits.slice(7, 9);
      return `+212 ${prefix} ${part1} ${part2} ${part3} ${part4}`;
    }
    return `+212 ${digits}`;
  }

  // If local 10-digit format (06 / 07 / 05)
  if (cleaned.length === 10 && cleaned.startsWith('0')) {
    const prefix = cleaned.slice(0, 2);
    const part1 = cleaned.slice(2, 4);
    const part2 = cleaned.slice(4, 6);
    const part3 = cleaned.slice(6, 8);
    const part4 = cleaned.slice(8, 10);
    return `${prefix} ${part1} ${part2} ${part3} ${part4}`;
  }

  // If 9 digits without leading zero (e.g. 610653694)
  if (cleaned.length === 9 && (cleaned.startsWith('6') || cleaned.startsWith('7') || cleaned.startsWith('5'))) {
    const prefix = '0' + cleaned.slice(0, 1);
    const part1 = cleaned.slice(1, 3);
    const part2 = cleaned.slice(3, 5);
    const part3 = cleaned.slice(5, 7);
    const part4 = cleaned.slice(7, 9);
    return `${prefix} ${part1} ${part2} ${part3} ${part4}`;
  }

  return raw;
}

/**
 * Formats carrier calls duration strings into localized, compact typography.
 * E.g. "00H 00Min 00Sec" -> "00h 00m 00s" (en) / "00h 00min 00s" (fr) / "00 س 00 د 00 ث" (ar)
 */
export function formatCallsDisplay(raw?: string | null, lang: LanguageCode = currentLanguage): string {
  if (!raw) {
    if (lang === 'ar') return '0 د';
    if (lang === 'zgh') return '0 ⵜⵙ';
    if (lang === 'fr') return '0m';
    return '0m';
  }

  // Normalize spaces and lowercase
  const str = raw.trim().replace(/\s+/g, ' ');

  // Extract H, Min, Sec values if present
  const hMatch = str.match(/(\d+)\s*(?:H|h|س|ⵜ)/i);
  const mMatch = str.match(/(\d+)\s*(?:Min|min|m|د|ⵜⵙ)/i);
  const sMatch = str.match(/(\d+)\s*(?:Sec|sec|s|ث|ⵜⵙⵏ)/i);

  if (hMatch || mMatch || sMatch) {
    const h = hMatch ? hMatch[1] : null;
    const m = mMatch ? mMatch[1] : null;
    const s = sMatch ? sMatch[1] : null;

    if (lang === 'ar') {
      const parts: string[] = [];
      if (h) parts.push(`${h}س`);
      if (m) parts.push(`${m}د`);
      if (s && !h) parts.push(`${s}ث`);
      return parts.length > 0 ? parts.join(' ') : str;
    }

    if (lang === 'zgh') {
      const parts: string[] = [];
      if (h) parts.push(`${h}ⵜ`);
      if (m) parts.push(`${m}ⵜⵙ`);
      if (s && !h) parts.push(`${s}ⵜⵙⵏ`);
      return parts.length > 0 ? parts.join(' ') : str;
    }

    if (lang === 'fr') {
      const parts: string[] = [];
      if (h) parts.push(`${h}h`);
      if (m) parts.push(`${m}m`);
      if (s && !h) parts.push(`${s}s`);
      return parts.length > 0 ? parts.join(' ') : str;
    }

    // Default English
    const parts: string[] = [];
    if (h) parts.push(`${h}h`);
    if (m) parts.push(`${m}m`);
    if (s && !h) parts.push(`${s}s`);
    return parts.length > 0 ? parts.join(' ') : str;
  }

  // If raw is already simple (e.g., "Illimité" or "Unlimited")
  if (str.toLowerCase().includes('illimit')) {
    if (lang === 'ar') return 'غير محدود';
    if (lang === 'zgh') return 'ⵡⴰⵔ ⴰⵎⵓⵔ';
    if (lang === 'en') return 'Unlimited';
    return 'Illimité';
  }

  return str;
}

/**
 * Formats breakdown labels dynamically according to current locale.
 */
export function formatBreakdownLabel(label: string, lang: LanguageCode = currentLanguage): string {
  const lower = label.trim().toLowerCase();

  if (lower.includes('solde principal') || lower.includes('solde') || lower.includes('main balance')) {
    if (lang === 'en') return 'Main Balance';
    if (lang === 'ar') return 'الرصيد الرئيسي';
    if (lang === 'zgh') return 'ⴰⵙⵉⴹⵏ ⴰⵎⵇⵔⴰⵏ';
    return 'Solde Principal';
  }

  if (lower.includes('internet') || lower.includes('data')) {
    if (lang === 'ar') return 'الإنترنت';
    if (lang === 'zgh') return 'ⵉⵏⵜⵉⵔⵏⵉⵜ';
    return 'Internet';
  }

  if (lower.includes('appel') || lower.includes('call') || lower.includes('voix') || lower.includes('voice')) {
    if (lang === 'en') return 'Calls';
    if (lang === 'ar') return 'المكالمات';
    if (lang === 'zgh') return 'ⵜⵉⵖⵔⵉⵡⵉⵏ';
    return 'Appels';
  }

  if (lower.includes('sms') || lower.includes('message')) {
    if (lang === 'ar') return 'الرسائل القصيرة';
    if (lang === 'zgh') return 'ⵜⵓⵣⵉⵏⵉⵏ';
    return 'SMS';
  }

  return label;
}
