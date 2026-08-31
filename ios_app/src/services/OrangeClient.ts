import { ConsumptionData, ConsumptionDetail } from '../types';

export class OrangeClient {
  private userAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36';
  private activeMsisdn: string;

  constructor(
    private loginId: string,
    private pass: string,
    private targetPhone: string = loginId,
    private savedCookies: string[] = []
  ) {
    this.activeMsisdn = targetPhone;
  }

  public get currentCookies(): string[] {
    return this.savedCookies;
  }

  public async fetchConsumption(): Promise<ConsumptionData> {
    // 1. Try to load /mon-solde directly with stored session
    try {
      const fastRes = await fetch('https://espace-client.orange.ma/mon-solde', {
        headers: {
          'User-Agent': this.userAgent,
          'Accept':
            'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
        },
      });
      const html = await fastRes.text();
      if (html.includes('Solde de recharge') || html.includes('mbm')) {
        return this.parseDashboard(html);
      }
    } catch (_e) {}

    // 2. Perform Full 3-Step Login Flow
    return await this.attemptFullLogin();
  }

  /**
   * Extracts the full outer HTML of a balanced <div> starting at startIndex
   */
  private extractBalancedDiv(html: string, startIndex: number): string {
    const tagRegex = /<div\b[^>]*>|<\/div>/gi;
    tagRegex.lastIndex = startIndex;
    let depth = 0;
    let match: RegExpExecArray | null;

    while ((match = tagRegex.exec(html)) !== null) {
      if (match[0].toLowerCase().startsWith('<div')) {
        depth++;
      } else {
        depth--;
        if (depth === 0) {
          return html.slice(startIndex, match.index + match[0].length);
        }
      }
    }
    return '';
  }

  private parseDashboard(html: string): ConsumptionData {
    // 1. Find all div.mbm containers using balanced tag matching (matching Jsoup in Android)
    const mbmOpenings = /<div\b[^>]*class=["\'][^"\']*\bmbm\b[^"\']*["\'][^>]*>/gi;
    let targetSection = '';
    let match: RegExpExecArray | null;

    while ((match = mbmOpenings.exec(html)) !== null) {
      const block = this.extractBalancedDiv(html, match.index);
      if (block.includes('Solde de recharge')) {
        targetSection = block;
        break;
      }
    }

    // 2. Fallback: If not found via class mbm, locate "Solde de recharge" and its enclosing container
    if (!targetSection && html.includes('Solde de recharge')) {
      const soldePos = html.indexOf('Solde de recharge');
      const lastDiv = html.lastIndexOf('<div', soldePos);
      if (lastDiv !== -1) {
        targetSection = this.extractBalancedDiv(html, lastDiv);
      }
      if (!targetSection) {
        // Safe window bounded strictly to Solde de recharge, avoiding header ads
        targetSection = html.slice(soldePos, soldePos + 2500);
      }
    }

    // Strip HTML tags to get pure text strictly from targetSection
    const cleanText = targetSection
      ? targetSection
          .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
          .replace(/<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>/gi, '')
          .replace(/<[^>]+>/g, ' ')
          .replace(/\s+/g, ' ')
      : '';

    const dhRegex = /\b(\d+(?:[.,]\d+)?)\s*Dh\b/i;
    const internetRegex = /\b(\d+(?:[.,]\d+)?)\s*(MO|GO|MB|GB)\b/i;
    const callsRegex = /\b(\d+h\s*\d+min(?:\s*\d+s)?|\d+min)\b/gi;
    const smsRegex = /\b(\d+)\s*SMS\b/i;

    const dhMatch = cleanText.match(dhRegex);
    const internetMatch = cleanText.match(internetRegex);
    const callsMatches: string[] = [];
    let cMatch: RegExpExecArray | null;
    while ((cMatch = callsRegex.exec(cleanText)) !== null) {
      callsMatches.push(cMatch[1]);
    }
    const smsMatch = cleanText.match(smsRegex);

    const phoneMatch = html.match(/\b(0[67]\d{8})\b/);
    const displayPhone = this.activeMsisdn.includes('@')
      ? (phoneMatch ? phoneMatch[1] : this.activeMsisdn)
      : this.activeMsisdn;

    const soldeDh = dhMatch ? dhMatch[0] : '0.00 Dh';
    const internet = internetMatch ? internetMatch[0] : '0 GO';
    const callsNational = callsMatches[0] || '0h 0min';
    const callsOrange = callsMatches[1] || '0h 0min';
    const callsInter = callsMatches[2] || '0h 0min';
    const sms = smsMatch ? smsMatch[0] : '0 SMS';

    const structuredDetails: ConsumptionDetail[] = [
      { label: 'Solde principal', value: soldeDh, iconType: 'wallet' },
      { label: 'Internet', value: internet, iconType: 'internet' },
      { label: 'Appels Nationaux', value: callsNational, iconType: 'calls' },
      { label: 'Appels Orange', value: callsOrange, iconType: 'orange' },
      { label: 'Appels Internationaux', value: callsInter, iconType: 'global' },
      { label: 'SMS', value: sms, iconType: 'sms' },
    ];

    return {
      operator: 'Orange',
      phoneNumber: displayPhone,
      callsRemaining: callsNational,
      callsPercent: null,
      internetRemaining: internet,
      internetPercent: null,
      extraDetails: soldeDh,
      structuredDetails,
    };
  }

  private async attemptFullLogin(): Promise<ConsumptionData> {
    const loginUrl = 'https://espace-client.orange.ma/sso/login';
    const getRes = await fetch(loginUrl, {
      headers: { 'User-Agent': this.userAgent },
    });
    const tokenHtml = await getRes.text();

    const tokenMatch1 = tokenHtml.match(
      /name=["\']login_form\[ezxform_token\]["\'][^>]*value=["\']([^"\']+)["\']/i
    );
    let token = tokenMatch1 ? tokenMatch1[1] : '';

    // Step 2: POST phone number
    const post1Params = new URLSearchParams();
    post1Params.append('login_form[login]', this.loginId);
    post1Params.append('login_form[ezxform_token]', token);

    const post1Res = await fetch(loginUrl, {
      method: 'POST',
      headers: {
        'User-Agent': this.userAgent,
        'Content-Type': 'application/x-www-form-urlencoded',
        'Origin': 'https://espace-client.orange.ma',
        'Referer': loginUrl,
      },
      body: post1Params.toString(),
    });
    const pwdHtml = await post1Res.text();

    const tokenMatch2 = pwdHtml.match(
      /name=["\']login_form\[ezxform_token\]["\'][^>]*value=["\']([^"\']+)["\']/i
    );
    if (tokenMatch2 && tokenMatch2[1]) {
      token = tokenMatch2[1];
    }

    // Step 3: POST password to /sso/check
    const checkUrl = 'https://espace-client.orange.ma/sso/check';
    const checkParams = new URLSearchParams();
    checkParams.append('login_form[_username]', this.loginId);
    checkParams.append('login_form[_password]', this.pass);
    checkParams.append('login_form[_remember_me]', '1');
    checkParams.append('login_form[ezxform_token]', token);

    const checkRes = await fetch(checkUrl, {
      method: 'POST',
      headers: {
        'User-Agent': this.userAgent,
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': loginUrl,
      },
      body: checkParams.toString(),
    });
    const checkBody = await checkRes.text();

    if (checkBody.includes('identifiants sont incorrects')) {
      throw new Error('Identifiant ou mot de passe Orange incorrect');
    }

    // Step 4: Fetch /mon-solde
    const soldeRes = await fetch('https://espace-client.orange.ma/mon-solde', {
      headers: {
        'User-Agent': this.userAgent,
        'Referer': 'https://espace-client.orange.ma/',
      },
    });
    const soldeHtml = await soldeRes.text();

    return this.parseDashboard(soldeHtml);
  }
}
