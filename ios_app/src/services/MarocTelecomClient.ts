import { ConsumptionData } from '../types';

export class MarocTelecomClient {
  private userAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36';

  constructor(
    private email: string,
    private pass: string,
    private phone: string,
    private savedCookies: string[] = []
  ) {}

  public get currentCookies(): string[] {
    return this.savedCookies;
  }

  private extractHiddenFields(html: string): Record<string, string> {
    const fields: Record<string, string> = {};
    const tagRegex = /<input\b[^>]*>/gi;
    let tagMatch: RegExpExecArray | null;
    while ((tagMatch = tagRegex.exec(html)) !== null) {
      const tag = tagMatch[0];
      const nameMatch = tag.match(/name=["\']([^"\']+)["\']/i);
      const valMatch = tag.match(/value=["\']([^"\']*)["\']/i);
      if (nameMatch) {
        fields[nameMatch[1]] = valMatch ? valMatch[1] : '';
      }
    }
    return fields;
  }

  public async fetchConsumption(): Promise<ConsumptionData> {
    // 1. Try to load Index.aspx directly if already logged in
    try {
      const dashUrl = 'https://selfcare.iam.ma/Particulier/Pages/Index.aspx';
      const dashRes = await fetch(dashUrl, {
        method: 'GET',
        headers: {
          'User-Agent': this.userAgent,
          'Accept':
            'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
          'Accept-Language': 'fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6',
        },
      });

      const dashHtml = await dashRes.text();
      if (dashHtml.includes('LoadTimerConsommation')) {
        return await this.executeConsumptionFetch(dashHtml);
      }
    } catch (_e) {}

    // 2. Perform Full Login
    return await this.attemptFullLogin();
  }

  private async executeConsumptionFetch(dashHtml: string): Promise<ConsumptionData> {
    const fields = this.extractHiddenFields(dashHtml);
    fields['ctl00$ScriptManager'] =
      'ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$upTimer|ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$LoadTimerConsommation';
    fields['__EVENTTARGET'] =
      'ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$LoadTimerConsommation';
    fields['__EVENTARGUMENT'] = '';
    fields['__ASYNCPOST'] = 'true';

    const formattedPhone = this.phone.startsWith('0')
      ? `212${this.phone.substring(1)}`
      : this.phone;

    fields[
      'ctl00$PlaceHolderHeaderNav$g_3f9f9e4a_13a8_42c6_80fd_ef2c3b395c76$ctl00$RtListeNumero$ctl00$HfNumeroAppel'
    ] = formattedPhone;
    fields[
      'ctl00$PlaceHolderHeaderNav$g_3f9f9e4a_13a8_42c6_80fd_ef2c3b395c76$ctl00$RtListeNumero$ctl00$produitID'
    ] = '2';
    fields[
      'ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$RtListeContrat$ctl00$HfNumeroAppel'
    ] = formattedPhone;
    fields[
      'ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$RtListeContrat$ctl00$HfProduitId'
    ] = '2';
    fields['ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$DdlProduit'] = '2';
    fields[
      'ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$ddlProduitConsommation'
    ] = '2';
    fields['ctl00$PlaceHolderMain$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0$ctl00$ddlProduitFidelio'] =
      '2';

    const bodyParams = new URLSearchParams();
    for (const [key, val] of Object.entries(fields)) {
      bodyParams.append(key, val);
    }

    const postRes = await fetch('https://selfcare.iam.ma/Particulier/Pages/Index.aspx', {
      method: 'POST',
      headers: {
        'User-Agent': this.userAgent,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest',
        'X-MicrosoftAjax': 'Delta=true',
        'Referer': 'https://selfcare.iam.ma/',
      },
      body: bodyParams.toString(),
    });

    const html = await postRes.text();

    const callsMatch = html.match(/lblCommunicationReste[^>]*>([^<]+)<\/label>/i);
    const intMatch = html.match(/lblInternetReste[^>]*>([^<]+)<\/label>/i);
    const callsPctMatch = html.match(
      /DivProgressCommunication[^>]*data-percent=["\']([^"\']+)["\']/i
    );
    const intPctMatch = html.match(
      /DivProgressInternet[^>]*data-percent=["\']([^"\']+)["\']/i
    );

    if (!callsMatch || !intMatch) {
      throw new Error(`Maroc Telecom balance parsing failed: ${html.substring(0, 120)}`);
    }

    return {
      operator: 'Maroc Telecom',
      phoneNumber: formattedPhone,
      callsRemaining: callsMatch[1].trim(),
      callsPercent: callsPctMatch ? parseFloat(callsPctMatch[1]) : null,
      internetRemaining: intMatch[1].trim(),
      internetPercent: intPctMatch ? parseFloat(intPctMatch[1]) : null,
    };
  }

  private async attemptFullLogin(): Promise<ConsumptionData> {
    const loginUrl = 'https://selfcare.iam.ma/Pages/Login.aspx';
    const getRes = await fetch(loginUrl, {
      headers: {
        'User-Agent': this.userAgent,
        'Accept':
          'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
        'Accept-Language': 'fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6',
      },
    });
    const getHtml = await getRes.text();

    const fields = this.extractHiddenFields(getHtml);

    let targetEvent = 'ctl00$ctl50$g_d8fa90bd_8360_4dd7_bea6_22fc238511fe$ctl00$lnkBtnConnex';
    const eventMatch = getHtml.match(/['"]([^'"]*lnkBtnConnex[^'"]*)['"]/i);
    if (eventMatch) {
      targetEvent = eventMatch[1].replace(/\\$/g, '$');
    }

    fields['__EVENTTARGET'] = targetEvent;
    fields['__EVENTARGUMENT'] = '';
    fields['ctl00$ctl50$g_d8fa90bd_8360_4dd7_bea6_22fc238511fe$ctl00$txtEmail'] = this.email;
    fields['ctl00$ctl50$g_d8fa90bd_8360_4dd7_bea6_22fc238511fe$ctl00$txtPassword'] = this.pass;

    const bodyParams = new URLSearchParams();
    for (const [key, val] of Object.entries(fields)) {
      bodyParams.append(key, val);
    }

    const postRes = await fetch(loginUrl, {
      method: 'POST',
      headers: {
        'User-Agent': this.userAgent,
        'Content-Type': 'application/x-www-form-urlencoded',
        'Referer': loginUrl,
      },
      body: bodyParams.toString(),
    });

    const postHtml = await postRes.text();
    if (postHtml.includes('LoadTimerConsommation')) {
      return await this.executeConsumptionFetch(postHtml);
    }

    const dashRes = await fetch('https://selfcare.iam.ma/Particulier/Pages/Index.aspx', {
      headers: {
        'User-Agent': this.userAgent,
        'Referer': loginUrl,
      },
    });

    const dashHtml = await dashRes.text();
    if (dashHtml.includes('LoadTimerConsommation')) {
      return await this.executeConsumptionFetch(dashHtml);
    }

    throw new Error('Identifiant ou mot de passe Maroc Telecom incorrect');
  }
}
