from rest_framework import serializers
from django.contrib.auth.hashers import check_password
from .models import UserAccount, Transaction


# ─────────────────────────────────────────────
# REGISTER
# ─────────────────────────────────────────────

class RegisterSerializer(serializers.ModelSerializer):
    pin = serializers.CharField(write_only=True, min_length=4, max_length=6)

    class Meta:
        model  = UserAccount
        fields = ['phone_number', 'full_name', 'pin']

    def validate_phone_number(self, value):
        value = value.strip().replace(' ', '')
        if UserAccount.objects.filter(phone_number=value).exists():
            raise serializers.ValidationError("Phone number already registered.")
        return value

    def validate_pin(self, value):
        if not value.isdigit():
            raise serializers.ValidationError("PIN must contain digits only.")
        return value

    def create(self, validated_data):
        raw_pin = validated_data.pop('pin')
        user    = UserAccount(**validated_data)
        user.set_pin(raw_pin)
        user.save()
        return user


# ─────────────────────────────────────────────
# LOGIN
# ─────────────────────────────────────────────

class LoginSerializer(serializers.Serializer):
    phone_number = serializers.CharField()
    pin          = serializers.CharField(write_only=True)

    def validate(self, data):
        phone = data.get('phone_number', '').strip()
        pin   = data.get('pin', '')

        # 1. Does user exist?
        try:
            user = UserAccount.objects.get(phone_number=phone)
        except UserAccount.DoesNotExist:
            raise serializers.ValidationError("Phone number not found.")

        # 2. Is account active?  (safe — getattr falls back to True if field missing)
        if not getattr(user, 'is_active', True):
            raise serializers.ValidationError("Account is disabled.")

        # 3. Check PIN against stored hash
        if not check_password(pin, user.pin_hash):
            raise serializers.ValidationError("Incorrect PIN.")

        data['user'] = user
        return data


# ─────────────────────────────────────────────
# PROFILE
# ─────────────────────────────────────────────

class UserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model        = UserAccount
        fields       = ['id', 'phone_number', 'full_name', 'balance', 'created_at']
        read_only_fields = fields


# ─────────────────────────────────────────────
# TRANSACTION
# ─────────────────────────────────────────────

class TransactionSerializer(serializers.ModelSerializer):
    party = serializers.SerializerMethodField()

    class Meta:
        model  = Transaction
        fields = [
            'id', 'amount', 'transaction_type',
            'description', 'reference', 'timestamp', 'party',
        ]

    def get_party(self, obj):
        """Return the name of the other side of the transaction."""
        request_user = self.context.get('request_user')
        if obj.transaction_type in ('SEND', 'WITHDRAW'):
            return obj.receiver.full_name if obj.receiver else 'Unknown'
        return obj.sender.full_name if obj.sender else 'Unknown'


# ─────────────────────────────────────────────
# SEND MONEY
# ─────────────────────────────────────────────

class SendMoneySerializer(serializers.Serializer):
    recipient_phone = serializers.CharField()
    amount          = serializers.DecimalField(max_digits=12, decimal_places=2)
    description     = serializers.CharField(required=False, default='', allow_blank=True)

    def validate_amount(self, value):
        if value <= 0:
            raise serializers.ValidationError("Amount must be greater than 0.")
        if value < 10:
            raise serializers.ValidationError("Minimum transfer is KES 10.")
        return value

    def validate_recipient_phone(self, value):
        if not UserAccount.objects.filter(phone_number=value.strip()).exists():
            raise serializers.ValidationError("Recipient phone number not found.")
        return value.strip()