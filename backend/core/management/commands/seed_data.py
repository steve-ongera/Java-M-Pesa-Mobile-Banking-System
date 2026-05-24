"""
Management command to seed the database with sample M-Pesa users and transactions.

Usage:
    python manage.py seed_data            # seed with defaults
    python manage.py seed_data --clear    # wipe existing data first, then seed
"""

import uuid
import random
from decimal import Decimal
from datetime import datetime, timedelta

from django.core.management.base import BaseCommand
from django.contrib.auth.hashers import make_password
from django.utils import timezone

from core.models import UserAccount, Transaction


# ── Sample users ──────────────────────────────────────────────────────────────
USERS = [
    {"full_name": "John Kamau",     "phone_number": "0712345678", "pin": "1234", "balance": "15000.00"},
    {"full_name": "Grace Wanjiku",  "phone_number": "0723456789", "pin": "2345", "balance": "8500.50"},
    {"full_name": "Brian Otieno",   "phone_number": "0734567890", "pin": "3456", "balance": "22300.75"},
    {"full_name": "Amina Hassan",   "phone_number": "0745678901", "pin": "4567", "balance": "5000.00"},
    {"full_name": "Peter Mwangi",   "phone_number": "0756789012", "pin": "5678", "balance": "31000.00"},
    {"full_name": "Fatuma Ali",     "phone_number": "0767890123", "pin": "6789", "balance": "9750.25"},
    {"full_name": "David Kipchoge", "phone_number": "0778901234", "pin": "7890", "balance": "47200.00"},
    {"full_name": "Sarah Njeri",    "phone_number": "0789012345", "pin": "8901", "balance": "3200.00"},
]

# ── Transaction templates ──────────────────────────────────────────────────────
SEND_DESCRIPTIONS = [
    "Rent payment",
    "School fees",
    "Grocery shopping",
    "Fuel money",
    "Birthday gift",
    "Loan repayment",
    "Business payment",
    "Fare refund",
    "Medical bill",
    "Utility bill",
]

DEPOSIT_DESCRIPTIONS = [
    "Salary deposit",
    "Business income",
    "M-Pesa deposit",
    "Bank transfer",
    "Savings top-up",
]


def make_reference():
    """Generate a unique MP-style reference: MP20240523AB12CD."""
    date_str = datetime.now().strftime("%Y%m%d")
    suffix   = uuid.uuid4().hex[:6].upper()
    return f"MP{date_str}{suffix}"


class Command(BaseCommand):
    help = "Seed the database with sample users and transactions for development."

    def add_arguments(self, parser):
        parser.add_argument(
            "--clear",
            action="store_true",
            help="Delete all existing users and transactions before seeding.",
        )

    def handle(self, *args, **options):
        if options["clear"]:
            self.stdout.write(self.style.WARNING("  Clearing existing data..."))
            Transaction.objects.all().delete()
            UserAccount.objects.all().delete()
            self.stdout.write(self.style.SUCCESS("  ✓ Existing data cleared.\n"))

        self.stdout.write(self.style.MIGRATE_HEADING("━━━ Seeding Users ━━━━━━━━━━━━━━━━━━━━━━"))
        created_users = self._seed_users()

        self.stdout.write(self.style.MIGRATE_HEADING("\n━━━ Seeding Transactions ━━━━━━━━━━━━━━━━"))
        self._seed_transactions(created_users)

        self.stdout.write(self.style.MIGRATE_HEADING("\n━━━ Summary ━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
        self.stdout.write(self.style.SUCCESS(
            f"  ✅ {UserAccount.objects.count()} users  |  "
            f"{Transaction.objects.count()} transactions in the database."
        ))
        self.stdout.write("")
        self.stdout.write("  Test accounts (phone → PIN):")
        for u in USERS:
            self.stdout.write(f"    {u['phone_number']}  →  {u['pin']}   ({u['full_name']})")
        self.stdout.write("")

    # ── Users ─────────────────────────────────────────────────────────────────

    def _seed_users(self):
        created = []

        for data in USERS:
            user, made = UserAccount.objects.get_or_create(
                phone_number=data["phone_number"],
                defaults={
                    "full_name": data["full_name"],
                    "pin_hash":  make_password(data["pin"]),
                    "balance":   Decimal(data["balance"]),
                },
            )

            if made:
                self.stdout.write(self.style.SUCCESS(
                    f"  ✓ Created  {user.full_name:<20} {user.phone_number}  "
                    f"KES {user.balance:>10}"
                ))
            else:
                self.stdout.write(self.style.WARNING(
                    f"  ~ Exists   {user.full_name:<20} {user.phone_number}  (skipped)"
                ))

            created.append(user)

        return created

    # ── Transactions ──────────────────────────────────────────────────────────

    def _seed_transactions(self, users):
        transactions = []
        used_refs    = set()

        def unique_ref():
            while True:
                ref = make_reference()
                if ref not in used_refs:
                    used_refs.add(ref)
                    return ref

        # 1. Deposits for every user (2 each)
        for user in users:
            for _ in range(2):
                amount = Decimal(str(random.randint(1000, 10000)))
                transactions.append(Transaction(
                    sender           = None,
                    receiver         = user,
                    amount           = amount,
                    transaction_type = "DEPOSIT",
                    description      = random.choice(DEPOSIT_DESCRIPTIONS),
                    reference        = unique_ref(),
                ))

        # 2. Sends between random pairs (20 transfers)
        for _ in range(20):
            sender, receiver = random.sample(users, 2)
            amount = Decimal(str(random.randint(100, 3000)))

            # Sender debit
            transactions.append(Transaction(
                sender           = sender,
                receiver         = receiver,
                amount           = amount,
                transaction_type = "SEND",
                description      = random.choice(SEND_DESCRIPTIONS),
                reference        = unique_ref(),
            ))

            # Receiver credit
            transactions.append(Transaction(
                sender           = sender,
                receiver         = receiver,
                amount           = amount,
                transaction_type = "RECEIVE",
                description      = random.choice(SEND_DESCRIPTIONS),
                reference        = unique_ref(),
            ))

        # 3. Withdrawals for a few users
        for user in random.sample(users, 3):
            amount = Decimal(str(random.randint(500, 2000)))
            transactions.append(Transaction(
                sender           = user,
                receiver         = None,
                amount           = amount,
                transaction_type = "WITHDRAW",
                description      = "ATM withdrawal",
                reference        = unique_ref(),
            ))

        Transaction.objects.bulk_create(transactions)
        self.stdout.write(self.style.SUCCESS(
            f"  ✓ Inserted {len(transactions)} transactions."
        ))