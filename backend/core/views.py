from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework import status
from django.contrib.hashers import make_password, check_password
from rest_framework_simplejwt.tokens import RefreshToken
from .models import UserAccount, Transaction
import uuid, random

@api_view(['POST'])
def login(request):
    phone = request.data.get('phone_number')
    pin   = request.data.get('pin')
    try:
        user = UserAccount.objects.get(phone_number=phone)
        if check_password(pin, user.pin_hash):
            refresh = RefreshToken.for_user(user)
            return Response({
                'token': str(refresh.access_token),
                'user': {'id': str(user.id),
                         'full_name': user.full_name,
                         'phone_number': user.phone_number}
            })
        return Response({'error': 'Invalid PIN'}, status=401)
    except UserAccount.DoesNotExist:
        return Response({'error': 'User not found'}, status=404)

@api_view(['POST'])
@permission_classes([IsAuthenticated])
def send_money(request):
    recipient_phone = request.data.get('recipient_phone')
    amount          = float(request.data.get('amount', 0))
    description     = request.data.get('description', '')

    sender = request.user  # from JWT
    if sender.balance < amount:
        return Response({'error': 'Insufficient balance'}, status=400)

    try:
        recipient = UserAccount.objects.get(phone_number=recipient_phone)
    except UserAccount.DoesNotExist:
        return Response({'error': 'Recipient not found'}, status=404)

    # Debit sender, credit recipient
    sender.balance   -= amount
    recipient.balance += amount
    sender.save()
    recipient.save()

    ref = f"MP-{uuid.uuid4().hex[:8].upper()}"
    Transaction.objects.create(
        sender=sender, receiver=recipient,
        amount=amount, transaction_type='SEND',
        description=description, reference=ref
    )

    return Response({'reference': ref, 'amount': amount,
                     'recipient': recipient.full_name,
                     'new_balance': str(sender.balance)})