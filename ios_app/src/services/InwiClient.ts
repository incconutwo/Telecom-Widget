import { ConsumptionData, ConsumptionDetail } from '../types';

export class InwiClient {
  private userAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36';
  private accessToken: string = '';
  private mdnToken: string = '';

  constructor(
    private phoneOrEmail: string,
    private pass: string,
    private selectedLine: string = phoneOrEmail,
    initialCookies: string[] = []
  ) {
    for (const cookie of initialCookies) {
      if (cookie.startsWith('accessToken=')) {
        this.accessToken = cookie.substring('accessToken='.length);
      } else if (cookie.startsWith('mdnToken=')) {
        this.mdnToken = cookie.substring('mdnToken='.length);
      }
    }
  }

  public get currentCookies(): string[] {
    return [
      `accessToken=${this.accessToken}`,
      `mdnToken=${this.mdnToken}`,
    ];
  }

  private baseHeaders(): Record<string, string> {
    return {
      'User-Agent': this.userAgent,
      'Content-Type': 'application/json',
      'Accept': 'application/json, text/plain, */*',
      'Origin': 'https://inwi.ma',
      'Referer': 'https://inwi.ma/',
      'sdata':
        'eyJjaGFubmVsIjoid2ViIiwiYXBwbGljYXRpb25fb3JpZ2luIjoibXlpbndpIiwidXVpZCI6ImMzN2NiYmIzLTc1ZDgtNDhmYy05OWNkLWVjNjNlNTEzMzAwMCIsImxhbmd1YWdlIjoiZnIiLCJhcHBWZXJzaW9uIjoxfQ==',
    };
  }

  private cleanPhone(phone: string): string {
    const trimmed = phone.trim().replace(/\s+/g, '');
    if (trimmed.startsWith('212')) {
      return '0' + trimmed.substring(3);
    }
    if (trimmed.startsWith('+212')) {
      return '0' + trimmed.substring(4);
    }
    return trimmed;
  }

  private async doLogin(): Promise<void> {
    const username = this.cleanPhone(this.phoneOrEmail);

    const res = await fetch('https://ms-prod.inwi.ma/api/ms-iam/v1/signin', {
      method: 'POST',
      headers: this.baseHeaders(),
      body: JSON.stringify({
        username,
        password: this.pass,
      }),
    });

    if (!res.ok) {
      const errText = await res.text();
      let msg = 'Identifiant ou mot de passe Inwi incorrect';
      try {
        const j = JSON.parse(errText);
        if (j.message) msg = j.message;
      } catch (_e) {}
      throw new Error(msg);
    }

    const json = await res.json();
    this.accessToken = json.accessToken || '';
  }

  public async fetchConsumption(): Promise<ConsumptionData> {
    if (!this.accessToken) {
      await this.doLogin();
    }

    const username = this.cleanPhone(this.phoneOrEmail);

    // 1. Fetch Profile
    const profileHeaders = {
      ...this.baseHeaders(),
      'Authorization': `Bearer ${this.accessToken}`,
      'Allow': 'GET',
    };

    let profileRes = await fetch('https://ms-prod.inwi.ma/api/ms-client/v1/profile', {
      headers: profileHeaders,
    });

    if (profileRes.status === 401 || profileRes.status === 403) {
      await this.doLogin();
      profileHeaders['Authorization'] = `Bearer ${this.accessToken}`;
      profileRes = await fetch('https://ms-prod.inwi.ma/api/ms-client/v1/profile', {
        headers: profileHeaders,
      });
    }

    if (!profileRes.ok) {
      throw new Error(`Profile query failed (${profileRes.status})`);
    }

    const profileJson = await profileRes.json();
    const linesArray = profileJson.lines || [];
    if (linesArray.length === 0) {
      throw new Error('Aucune ligne Inwi trouvée sur ce compte');
    }

    let targetLineObj = linesArray.find(
      (l: any) =>
        l.mdn === this.selectedLine ||
        `${l.mdn} - ${l.offer_name_fr}` === this.selectedLine ||
        l.mdn === username
    );
    if (!targetLineObj) {
      targetLineObj = linesArray.find((l: any) => l.isMain) || linesArray[0];
    }

    const mdn = targetLineObj.mdn || username;
    this.mdnToken = targetLineObj.mdnSegmentationToken || '';

    // 2. Fetch Balances
    const balHeaders = {
      ...this.baseHeaders(),
      'Authorization': `Bearer ${this.accessToken}`,
      'mdn-segmentation-token': `Bearer ${this.mdnToken}`,
    };

    const balRes = await fetch('https://ms-prod.inwi.ma/api/ms-balance/v1/balances', {
      headers: balHeaders,
    });

    if (!balRes.ok) {
      throw new Error(`Balance query failed (${balRes.status})`);
    }

    const balJson = await balRes.json();
    return this.parseBalances(balJson, mdn);
  }

  private parseBalances(root: any, currentMdn: string): ConsumptionData {
    const categories = root?.categorie || [];
    if (categories.length === 0) {
      return {
        operator: 'Inwi',
        phoneNumber: currentMdn,
        callsRemaining: '0h 0min',
        internetRemaining: '0 Go',
      };
    }

    let internet = '0 Go';
    let calls = '0h 0min';
    let soldeExtra = '';
    const structured: ConsumptionDetail[] = [];

    for (const cat of categories) {
      const subCats =
        cat.sub_categories && cat.sub_categories.length > 0 ? cat.sub_categories : [cat];

      for (const sub of subCats) {
        const nameFr = sub.name_fr || cat.name_fr || 'Solde';
        const balVal = sub.balance_value || cat.balance_value || '0';
        const unit = sub.unit || cat.unit || '';
        const valStr = `${balVal} ${unit}`.trim();

        if (nameFr.toLowerCase().includes('dirham') || unit.toLowerCase() === 'dhs') {
          const raw = parseFloat(balVal) || 0;
          const dh = raw > 100 ? raw / 100 : raw;
          const formattedSolde = `${dh.toFixed(2)} DH`;
          soldeExtra = formattedSolde;
          structured.push({ label: nameFr, value: formattedSolde, iconType: 'wallet' });
        } else if (
          nameFr.toLowerCase().includes('internet') ||
          ['go', 'mo', 'gb', 'mb'].includes(unit.toLowerCase())
        ) {
          if (internet === '0 Go' || internet === 'N/A') internet = valStr;
          structured.push({ label: nameFr, value: valStr, iconType: 'internet' });
        } else if (nameFr.toLowerCase().includes('sms') || unit.toLowerCase().includes('sms')) {
          structured.push({ label: nameFr, value: valStr, iconType: 'sms' });
        } else {
          if (calls === '0h 0min' || calls === 'N/A') calls = valStr;
          structured.push({ label: nameFr, value: valStr, iconType: 'calls' });
        }
      }
    }

    return {
      operator: 'Inwi',
      phoneNumber: currentMdn,
      callsRemaining: calls,
      callsPercent: null,
      internetRemaining: internet,
      internetPercent: null,
      extraDetails: soldeExtra,
      structuredDetails: structured,
    };
  }
}
