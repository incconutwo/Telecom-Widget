import requests
import re
from bs4 import BeautifulSoup

class MultiLineException(Exception):
    def __init__(self, lines, token, session):
        self.lines = lines
        self.token = token
        self.session = session
        super().__init__("Multi-line account detected")

class OrangeClient:
    def __init__(self, login, password, selected_line=None):
        self.login = login
        self.password = password
        self.selected_line = selected_line or login
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        })

    def authenticate(self):
        payload = {
            "login_form[login]": self.login,
            "login_form[password]": self.password
        }
        res = self.session.post(
            "https://espace-client.orange.ma/api/login_check",
            data=payload,
            headers={"X-Requested-With": "XMLHttpRequest"}
        )
        if res.status_code != 200 or "error" in res.text:
            raise Exception("Identifiant ou mot de passe Orange incorrect")
        return True

    def fetch_consumption(self):
        res = self.session.get("https://espace-client.orange.ma/dashboard")
        html = res.text

        if "choisir-ligne" in html or "form_select_line" in html:
            soup = BeautifulSoup(html, "html.parser")
            lines = [opt.get_text(strip=True) for opt in soup.select("select[name*=line] option, input[name*=line], .line-item") if opt.get_text(strip=True)]
            token_input = soup.select_one("input[name*=_token]")
            token = token_input["value"] if token_input else ""
            if lines:
                raise MultiLineException(lines, token, self.session)

        if "login_form" in html or "/login" in res.url:
            self.authenticate()
            res = self.session.get("https://espace-client.orange.ma/dashboard")
            html = res.text

        return self.parse_dashboard(html)

    def parse_dashboard(self, html):
        soup = BeautifulSoup(html, "html.parser")
        internet = "N/A"
        calls = "N/A"
        solde = ""
        details = []

        for item in soup.select(".conso-item, .card-conso, .solde-card, .progress-conso"):
            txt = item.get_text(" ", strip=True)
            if not txt:
                continue
            if any(k in txt.lower() for k in ["go", "mo", "internet", "gb", "mb"]):
                if internet == "N/A":
                    internet = txt
                details.append(f"Internet: {txt}")
            elif any(k in txt.lower() for k in ["h", "min", "appel", "voix"]):
                if calls == "N/A":
                    calls = txt
                details.append(f"Calls: {txt}")
            elif "dh" in txt.lower() or "solde" in txt.lower():
                if not solde:
                    solde = txt
                details.append(f"Solde: {txt}")

        if not details:
            body_txt = soup.get_text()
            go_match = re.search(r'(\d+[\.,]?\d*\s*(?:Go|Mo|GB|MB))', body_txt, re.IGNORECASE)
            call_match = re.search(r'(\d+\s*H(?:\s*\d+\s*min)?|\d+\s*min)', body_txt, re.IGNORECASE)
            dh_match = re.search(r'(\d+[\.,]?\d*\s*DH)', body_txt, re.IGNORECASE)

            if go_match:
                internet = go_match.group(1)
                details.append(f"Internet: {internet}")
            if call_match:
                calls = call_match.group(1)
                details.append(f"Calls: {calls}")
            if dh_match:
                solde = dh_match.group(1)
                details.append(f"Solde: {solde}")

        return {
            "operator": "Orange",
            "phone": self.selected_line,
            "internet": internet,
            "calls": calls,
            "solde": solde,
            "details": details
        }
