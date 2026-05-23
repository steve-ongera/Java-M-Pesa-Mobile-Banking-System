import uuid
from django.db import models
from django.contrib.auth.hashers import make_password


class UserAccount(models.Model):
    id           = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    phone_number = models.CharField(max_length=15, unique=True)
    pin_hash     = models.CharField(max_length=255)
    full_name    = models.CharField(max_length=100)
    balance      = models.DecimalField(max_digits=12, decimal_places=2, default=0.00)
    is_active    = models.BooleanField(default=True)
    created_at   = models.DateTimeField(auto_now_add=True)
    updated_at   = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'user_accounts'
        ordering = ['-created_at']

    def set_pin(self, raw_pin):
        self.pin_hash = make_password(raw_pin)

    def __str__(self):
        return f"{self.full_name} ({self.phone_number})"


class Transaction(models.Model):
    TRANSACTION_TYPES = [
        ('SEND',     'Send Money'),
        ('RECEIVE',  'Receive Money'),
        ('DEPOSIT',  'Deposit'),
        ('WITHDRAW', 'Withdraw'),
    ]

    STATUS_CHOICES = [
        ('PENDING',   'Pending'),
        ('COMPLETED', 'Completed'),
        ('FAILED',    'Failed'),
    ]

    id               = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    sender           = models.ForeignKey(
                            UserAccount, on_delete=models.SET_NULL,
                            null=True, blank=True, related_name='sent_transactions')
    receiver         = models.ForeignKey(
                            UserAccount, on_delete=models.SET_NULL,
                            null=True, blank=True, related_name='received_transactions')
    amount           = models.DecimalField(max_digits=12, decimal_places=2)
    transaction_type = models.CharField(max_length=10, choices=TRANSACTION_TYPES)
    status           = models.CharField(max_length=10, choices=STATUS_CHOICES, default='COMPLETED')
    description      = models.TextField(blank=True, default='')
    reference        = models.CharField(max_length=30, unique=True)
    timestamp        = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'transactions'
        ordering = ['-timestamp']

    def __str__(self):
        return f"{self.transaction_type} KES {self.amount} | Ref: {self.reference}"
