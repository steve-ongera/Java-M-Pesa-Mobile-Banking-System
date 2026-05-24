import uuid
from django.db import models
from django.contrib.auth.hashers import make_password, check_password


class UserAccount(models.Model):
    id           = models.UUIDField(primary_key=True, default=uuid.uuid4)
    phone_number = models.CharField(max_length=15, unique=True)
    pin_hash     = models.CharField(max_length=255)
    full_name    = models.CharField(max_length=100)
    balance      = models.DecimalField(max_digits=12, decimal_places=2, default=0.00)
    is_active    = models.BooleanField(default=True)        # added - used by serializer
    created_at   = models.DateTimeField(auto_now_add=True)

    def set_pin(self, raw_pin):
        self.pin_hash = make_password(raw_pin)

    def check_pin(self, raw_pin):
        return check_password(raw_pin, self.pin_hash)

    def __str__(self):
        return f"{self.full_name} ({self.phone_number})"


class Transaction(models.Model):
    TYPES = [
        ('SEND',     'Send Money'),
        ('RECEIVE',  'Receive Money'),
        ('DEPOSIT',  'Deposit'),
        ('WITHDRAW', 'Withdraw'),
    ]

    id               = models.UUIDField(primary_key=True, default=uuid.uuid4)
    sender           = models.ForeignKey(UserAccount, on_delete=models.SET_NULL,
                                         null=True, related_name='sent')
    receiver         = models.ForeignKey(UserAccount, on_delete=models.SET_NULL,
                                         null=True, related_name='received')
    amount           = models.DecimalField(max_digits=12, decimal_places=2)
    transaction_type = models.CharField(max_length=10, choices=TYPES)
    description      = models.TextField(blank=True)
    timestamp        = models.DateTimeField(auto_now_add=True)
    reference        = models.CharField(max_length=20, unique=True)

    def __str__(self):
        return f"{self.transaction_type} KES {self.amount} @ {self.timestamp}"