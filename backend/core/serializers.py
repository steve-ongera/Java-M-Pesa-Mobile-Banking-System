from rest_framework import serializers
from django.contrib.auth.hashers import check_password
from .models import UserAccount, Transaction


# ──────────────────────────────────────────────
# USER SERIALIZERS
# ──────────────────────────────────────────────

class RegisterSerializer(serializers.ModelSerializer):
    pin = serializers.CharField(write_only=True, min_length=4, max_length=6)

    class Meta:
        model  = UserAccount
        fields = ['phone_number', 'full_name', 'pin']

    def validate_phone_number(self, value):
        value = value.strip().replace(' ', '')
        if not value.startswith('07') and not value.startswith('+254'):
            raise serializers.ValidationError(
                "Phone number must start with 07 or +254"
            )
        if UserAccount.objects.filter(phone_number=value).exists():
            raise serializers.ValidationError("Phone number already registered.")
        return value

    def validate_pin(self, value):
        if not value.isdigit():
            raise serializers.ValidationError("PIN must contain digits only.")
        return value

    def create(self, validated_data):
        raw_pin = validated_data.pop('pin')
        user = UserAccount(**validated_data)
        user.set_pin(raw_pin)
        user.save()
        return user


class LoginSerializer(serializers.Serializer):
    phone_number = serializers.CharField()
    pin          = serializers.CharField(write_only=True)

    def validate(self, data):
        phone = data.get('phone_number').strip()
        pin   = data.get('pin')

        try:
            user = UserAccount.objects.get(phone_number=phone)
        except UserAccount.DoesNotExist:
            raise serializers.ValidationError("User with this phone number not found.")

        if not user.is_active:
            raise serializers.ValidationError("Account is disabled.")

        if not check_password(pin, user.pin_hash):
            raise serializers.ValidationError("Incorrect PIN.")

        data['user'] = user
        return data


class UserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model  = UserAccount
        fields = ['id', 'phone_number', 'full_name', 'balance', 'created_at']
        read_only_fields = fields


# ──────────────────────────────────────────────
# TRANSACTION SERIALIZERS
# ──────────────────────────────────────────────

class TransactionSerializer(serializers.ModelSerializer):
    sender_name   = serializers.SerializerMethodField()
    receiver_name = serializers.SerializerMethodField()
    party         = serializers.SerializerMethodField()

    class Meta:
        model  = Transaction
        fields = [
            'id', 'amount', 'transaction_type', 'status',
            'description', 'reference', 'timestamp',
            'sender_name', 'receiver_name', 'party',
        ]

    def get_sender_name(self, obj):
        return obj.sender.full_name if obj.sender else 'Unknown'

    def get_receiver_name(self, obj):
        return obj.receiver.full_name if obj.receiver else 'Unknown'

    def get_party(self, obj):
        """Return the 'other side' name for display in statement."""
        request_user = self.context.get('request_user')
        if obj.transaction_type in ('SEND', 'WITHDRAW'):
            return obj.receiver.full_name if obj.receiver else 'Unknown'
        return obj.sender.full_name if obj.sender else 'Unknown'


class SendMoneySerializer(serializers.Serializer):
    recipient_phone = serializers.CharField()
    amount          = serializers.DecimalField(max_digits=12, decimal_places=2)
    description     = serializers.CharField(required=False, default='', allow_blank=True)

    def validate_amount(self, value):
        if value <= 0:
            raise serializers.ValidationError("Amount must be greater than 0.")
        if value < 10:
            raise serializers.ValidationError("Minimum transfer amount is KES 10.")
        return value

    def validate_recipient_phone(self, value):
        value = value.strip()
        if not UserAccount.objects.filter(phone_number=value).exists():
            raise serializers.ValidationError("Recipient phone number not found.")
        return value