export class CookieJar {
  private cookies: Map<string, string> = new Map();

  constructor(initialCookieStrings: string[] = []) {
    this.addCookies(initialCookieStrings);
  }

  public addCookies(cookieStrings: string[]) {
    for (const str of cookieStrings) {
      if (!str) continue;
      const parts = str.split(';')[0].trim();
      const eqIdx = parts.indexOf('=');
      if (eqIdx > 0) {
        const name = parts.substring(0, eqIdx).trim();
        const value = parts.substring(eqIdx + 1).trim();
        this.cookies.set(name, value);
      }
    }
  }

  public parseResponseHeaders(headers: Headers) {
    // In React Native / Expo fetch, Set-Cookie can be accessed via headers.get('set-cookie') or getSetCookie()
    const setCookie = (headers as any).getSetCookie ? (headers as any).getSetCookie() : [headers.get('set-cookie')];
    if (Array.isArray(setCookie)) {
      this.addCookies(setCookie.filter(Boolean));
    } else if (typeof setCookie === 'string') {
      this.addCookies([setCookie]);
    }
  }

  public getCookieHeader(): string {
    const list: string[] = [];
    this.cookies.forEach((val, key) => {
      list.push(`${key}=${val}`);
    });
    return list.join('; ');
  }

  public getAllCookieStrings(): string[] {
    const list: string[] = [];
    this.cookies.forEach((val, key) => {
      list.push(`${key}=${val}`);
    });
    return list;
  }
}
