import uuid
from decimal import Decimal

from django.db import transaction as db_transaction
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

from .models import UserAccount, Transaction
from .serializers import (
    RegisterSerializer,
    LoginSerializer,
    UserProfileSerializer,
    TransactionSerializer,
    SendMoneySerializer,
)
from .utils import generate_reference, get_user_from_token, token_for_user


# ──────────────────────────────────────────────
# AUTH VIEWS
# ──────────────────────────────────────────────

@api_view(['POST'])
@permission_classes([AllowAny])
def register(request):
    serializer = RegisterSerializer(data=request.data)
    if serializer.is_valid():
        user = serializer.save()
        return Response({
            'message': 'Account created successfully.',
            'user': {
                'id':           str(user.id),
                'full_name':    user.full_name,
                'phone_number': user.phone_number,
            }
        }, status=status.HTTP_201_CREATED)

    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['POST'])
@permission_classes([AllowAny])
def login(request):
    serializer = LoginSerializer(data=request.data)
    if serializer.is_valid():
        user  = serializer.validated_data['user']
        token = token_for_user(user)
        return Response({
            'token': token,
            'user': {
                'id':           str(user.id),
                'full_name':    user.full_name,
                'phone_number': user.phone_number,
            }
        }, status=status.HTTP_200_OK)

    # Flatten first error message for the Java client
    errors = serializer.errors
    if 'non_field_errors' in errors:
        msg = errors['non_field_errors'][0]
    else:
        msg = list(errors.values())[0][0]
    return Response({'error': str(msg)}, status=status.HTTP_401_UNAUTHORIZED)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def logout(request):
    return Response({'message': 'Logged out successfully.'}, status=status.HTTP_200_OK)


# ──────────────────────────────────────────────
# ACCOUNT VIEWS
# ──────────────────────────────────────────────

@api_view(['GET'])
@permission_classes([IsAuthenticated])
def profile(request):
    user = get_user_from_token(request)
    if not user:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    serializer = UserProfileSerializer(user)
    return Response(serializer.data, status=status.HTTP_200_OK)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def balance(request):
    user = get_user_from_token(request)
    if not user:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    return Response({
        'balance':  str(user.balance),
        'currency': 'KES',
        'account':  user.phone_number,
    }, status=status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def send_money(request):
    sender = get_user_from_token(request)
    if not sender:
        return Response({'error': 'Sender not found.'}, status=status.HTTP_404_NOT_FOUND)

    serializer = SendMoneySerializer(data=request.data)
    if not serializer.is_valid():
        first_error = list(serializer.errors.values())[0][0]
        return Response({'error': str(first_error)}, status=status.HTTP_400_BAD_REQUEST)

    recipient_phone = serializer.validated_data['recipient_phone']
    amount          = Decimal(str(serializer.validated_data['amount']))
    description     = serializer.validated_data.get('description', '')

    if sender.phone_number == recipient_phone:
        return Response({'error': 'Cannot send money to yourself.'},
                        status=status.HTTP_400_BAD_REQUEST)

    if sender.balance < amount:
        return Response({
            'error': f'Insufficient balance. Your balance is KES {sender.balance}.'
        }, status=status.HTTP_400_BAD_REQUEST)

    try:
        recipient = UserAccount.objects.get(phone_number=recipient_phone)
    except UserAccount.DoesNotExist:
        return Response({'error': 'Recipient not found.'}, status=status.HTTP_404_NOT_FOUND)

    with db_transaction.atomic():
        sender.balance    -= amount
        recipient.balance += amount
        # Only save fields that exist on the model
        sender.save(update_fields=['balance'])
        recipient.save(update_fields=['balance'])

        ref = generate_reference()

        Transaction.objects.create(
            sender=sender, receiver=recipient,
            amount=amount, transaction_type='SEND',
            description=description, reference=ref,
        )
        Transaction.objects.create(
            sender=sender, receiver=recipient,
            amount=amount, transaction_type='RECEIVE',
            description=description, reference=generate_reference(),
        )

    return Response({
        'message':     'Transfer successful.',
        'reference':   ref,
        'amount':      str(amount),
        'recipient':   recipient.full_name,
        'new_balance': str(sender.balance),
        'currency':    'KES',
    }, status=status.HTTP_200_OK)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def statement(request):
    user = get_user_from_token(request)
    if not user:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    limit = int(request.query_params.get('limit', 20))
    limit = min(limit, 50)

    # Combine sent + received, deduplicate, sort
    txns = (
        Transaction.objects.filter(sender=user) |
        Transaction.objects.filter(receiver=user)
    ).distinct().order_by('-timestamp')[:limit]

    serializer = TransactionSerializer(txns, many=True, context={'request_user': user})
    return Response({
        'account':      user.phone_number,
        'count':        len(serializer.data),
        'transactions': serializer.data,
    }, status=status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def deposit(request):
    user = get_user_from_token(request)
    if not user:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    try:
        amount = Decimal(str(request.data.get('amount', 0)))
    except Exception:
        return Response({'error': 'Invalid amount.'}, status=status.HTTP_400_BAD_REQUEST)

    desc = request.data.get('description', 'Deposit')

    if amount <= 0:
        return Response({'error': 'Amount must be greater than 0.'},
                        status=status.HTTP_400_BAD_REQUEST)

    with db_transaction.atomic():
        user.balance += amount
        user.save(update_fields=['balance'])

        ref = generate_reference()
        Transaction.objects.create(
            sender=None, receiver=user,
            amount=amount, transaction_type='DEPOSIT',
            description=desc, reference=ref,
        )

    return Response({
        'message':     'Deposit successful.',
        'reference':   ref,
        'amount':      str(amount),
        'new_balance': str(user.balance),
        'currency':    'KES',
    }, status=status.HTTP_200_OK)