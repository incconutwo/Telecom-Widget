import requests
import re
from bs4 import BeautifulSoup

class MarocTelecomClient:
    def __init__(self, email, password, phone=""):
        self.email = email
        self.password = password
        self.phone = phone
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        })

    def authenticate(self):
        login_page_url = "https://monespace.iam.ma/login.aspx"
        res = self.session.get(login_page_url)
        soup = BeautifulSoup(res.text, "html.parser")

        view_state = soup.find("input", {"name": "__VIEWSTATE"})
        view_state_gen = soup.find("input", {"name": "__VIEWSTATEGENERATOR"})
        event_val = soup.find("input", {"name": "__EVENTVALIDATION"})

        payload = {
            "__VIEWSTATE": view_state["value"] if view_state else "",
            "__VIEWSTATEGENERATOR": view_state_gen["value"] if view_state_gen else "",
            "__EVENTVALIDATION": event_val["value"] if event_val else "",
            "ctl00$ContentPlaceHolder1$txtLogin": self.email,
            "ctl00$ContentPlaceHolder1$txtPassword": self.password,
            "ctl00$ContentPlaceHolder1$btnValider": "Se connecter"
        }

        post_res = self.session.post(login_page_url, data=payload)
        if "Identifiant ou mot de passe incorrect" in post_res.text or "Mot de passe incorrect" in post_res.text:
            raise Exception("Identifiant ou mot de passe incorrect")
        return True

    def fetch_consumption(self):
        url = "https://monespace.iam.ma/MonCompte/Index.aspx"
        res = self.session.get(url)
        if "login.aspx" in res.url or "txtLogin" in res.text:
            self.authenticate()
            res = self.session.get(url)
        return self.parse_dashboard(res.text)

    def parse_dashboard(self, html):
        soup = BeautifulSoup(html, "html.parser")
        internet = "N/A"
        calls = "N/A"
        solde = ""
        details = []

        balances = soup.select(".solde-item, .item-conso, .bloc-conso, .conso-item, .table-conso tr")
        for item in balances:
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
                details.append(f"Solde: {solde}")

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
            "operator": "Maroc Telecom",
            "phone": self.phone or self.email,
            "internet": internet,
            "calls": calls,
            "solde": solde,
            "details": details
        }
