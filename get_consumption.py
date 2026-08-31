import sys
import argparse
from iam_client import MarocTelecomClient
from orange_client import OrangeClient

def main():
    parser = argparse.ArgumentParser(description="Telecom Mobile Balance CLI")
    parser.add_argument("--operator", choices=["iam", "orange"], required=True, help="Operator: iam or orange")
    parser.add_argument("--login", required=True, help="Email (for IAM) or Phone Number (for Orange)")
    parser.add_argument("--password", required=True, help="Password")
    parser.add_argument("--phone", help="Phone number (for IAM multi-line/sub-accounts)")
    parser.add_argument("--line", help="Specific line to select (for Orange)")

    args = parser.parse_args()

    if args.operator == "iam":
        client = MarocTelecomClient(args.login, args.password, args.phone or "")
    else:
        client = OrangeClient(args.login, args.password, args.line or args.login)

    data = client.fetch_consumption()
    print(f"\nOperator: {data.get('operator')}")
    print(f"Phone: {data.get('phone')}")
    print(f"Internet: {data.get('internet')}")
    print(f"Calls: {data.get('calls')}")
    if data.get("solde"):
        print(f"Solde: {data.get('solde')}")

    if data.get("details"):
        print("\nDetails:")
        for d in data["details"]:
            print(f" - {d}")

if __name__ == "__main__":
    main()
