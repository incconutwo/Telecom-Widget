import requests
import json
import sys
import os

# Set output encoding to UTF-8
sys.stdout.reconfigure(encoding='utf-8')

def get_inwi_data(username, password):
    print(f"1. Logging in as {username}...")
    
    session = requests.Session()
    
    # Common headers
    base_headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
        "Content-Type": "application/json",
        "Accept": "application/json, text/plain, */*",
        "Origin": "https://inwi.ma",
        "Referer": "https://inwi.ma/",
        "sdata": "eyJjaGFubmVsIjoid2ViIiwiYXBwbGljYXRpb25fb3JpZ2luIjoibXlpbndpIiwidXVpZCI6ImMzN2NiYmIzLTc1ZDgtNDhmYy05OWNkLWVjNjNlNTEzMzAwMCIsImxhbmd1YWdlIjoiZnIiLCJhcHBWZXJzaW9uIjoxfQ=="
    }
    
    # 1. Signin
    login_url = "https://ms-prod.inwi.ma/api/ms-iam/v1/signin"
    login_payload = {
        "username": username,
        "password": password
    }
    
    resp = session.post(login_url, json=login_payload, headers=base_headers)
    if resp.status_code != 200:
        print(f"❌ Login failed! Status: {resp.status_code}")
        print(resp.text)
        return
        
    auth_data = resp.json()
    access_token = auth_data.get("accessToken")
    print("✅ Login successful! Got access token.")
    
    # 2. Get Profile & Line segmentation tokens
    print("\n2. Fetching profile & line tokens...")
    profile_url = "https://ms-prod.inwi.ma/api/ms-client/v1/profile"
    auth_headers = base_headers.copy()
    auth_headers["Authorization"] = f"Bearer {access_token}"
    auth_headers["Allow"] = "GET"
    
    prof_resp = session.get(profile_url, headers=auth_headers)
    if prof_resp.status_code != 200:
        print(f"❌ Profile failed! Status: {prof_resp.status_code}")
        print(prof_resp.text)
        return
        
    profile_data = prof_resp.json()
    lines = profile_data.get("lines", [])
    print(f"✅ Found {len(lines)} line(s):")
    for l in lines:
        print(f"   - {l.get('mdn')} ({l.get('offer_name_fr')})")
        
    if not lines:
        print("No lines found!")
        return
        
    # Take the first / main line
    selected_line = lines[0]
    mdn_token = selected_line.get("mdnSegmentationToken")
    print(f"\nUsing line {selected_line.get('mdn')} with mdnSegmentationToken")
    
    # 3. Get Balances
    print("\n3. Fetching live balances...")
    balances_url = "https://ms-prod.inwi.ma/api/ms-balance/v1/balances"
    bal_headers = auth_headers.copy()
    bal_headers["mdn-segmentation-token"] = f"Bearer {mdn_token}"
    
    bal_resp = session.get(balances_url, headers=bal_headers)
    if bal_resp.status_code != 200:
        print(f"❌ Balances failed! Status: {bal_resp.status_code}")
        print(bal_resp.text)
        return
        
    balances_data = bal_resp.json()
    print("✅ LIVE BALANCES RECEIVED:")
    print(json.dumps(balances_data, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    test_user = os.getenv("INWI_USER", "0600000000")
    test_pass = os.getenv("INWI_PASS", "password123")
    get_inwi_data(test_user, test_pass)
