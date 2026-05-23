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
    """
    Register a new user account.
    POST /api/auth/register/
    Body: { phone_number, full_name, pin }
    """
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
    """
    Login with phone number and PIN.
    POST /api/auth/login/
    Body: { phone_number, pin }
    Returns: JWT access token
    """
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

    return Response({'error': list(serializer.errors.values())[0][0]},
                    status=status.HTTP_401_UNAUTHORIZED)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def logout(request):
    """
    Logout — client must discard the token.
    POST /api/auth/logout/
    """
    return Response({'message': 'Logged out successfully.'}, status=status.HTTP_200_OK)


# ──────────────────────────────────────────────
# ACCOUNT VIEWS
# ──────────────────────────────────────────────

@api_view(['GET'])
@permission_classes([IsAuthenticated])
def profile(request):
    """
    Get authenticated user profile.
    GET /api/account/profile/
    """
    user = get_user_from_token(request)
    if not user:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    serializer = UserProfileSerializer(user)
    return Response(serializer.data, status=status.HTTP_200_OK)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def balance(request):
    """
    Get current account balance.
    GET /api/account/balance/
    """
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
    """
    Send money to another user.
    POST /api/account/send/
    Body: { recipient_phone, amount, description }
    """
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

    # Prevent sending to self
    if sender.phone_number == recipient_phone:
        return Response({'error': 'Cannot send money to yourself.'},
                        status=status.HTTP_400_BAD_REQUEST)

    # Check balance
    if sender.balance < amount:
        return Response({
            'error': f'Insufficient balance. Your balance is KES {sender.balance}.'
        }, status=status.HTTP_400_BAD_REQUEST)

    try:
        recipient = UserAccount.objects.get(phone_number=recipient_phone)
    except UserAccount.DoesNotExist:
        return Response({'error': 'Recipient not found.'}, status=status.HTTP_404_NOT_FOUND)

    # Atomic transfer
    with db_transaction.atomic():
        sender.balance   -= amount
        recipient.balance += amount
        sender.save(update_fields=['balance', 'updated_at'])
        recipient.save(update_fields=['balance', 'updated_at'])

        ref = generate_reference()

        # Debit record for sender
        Transaction.objects.create(
            sender=sender, receiver=recipient,
            amount=amount, transaction_type='SEND',
            description=description, reference=ref, status='COMPLETED',
        )
        # Credit record for receiver (separate reference)
        Transaction.objects.create(
            sender=sender, receiver=recipient,
            amount=amount, transaction_type='RECEIVE',
            description=description,
            reference=generate_reference(),
            status='COMPLETED',
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
    """
    Get mini statement (last 20 transactions).
    GET /api/account/statement/
    Optional query param: ?limit=10
    """
    user  = get_user_from_token(request)
    if not user:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    limit = int(request.query_params.get('limit', 20))
    limit = min(limit, 50)  # cap at 50

    txns = Transaction.objects.filter(
        sender=user
    ) | Transaction.objects.filter(
        receiver=user
    )
    txns = txns.order_by('-timestamp')[:limit]

    serializer = TransactionSerializer(
        txns, many=True, context={'request_user': user}
    )
    return Response({
        'account':      user.phone_number,
        'count':        txns.count(),
        'transactions': serializer.data,
    }, status=status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def deposit(request):
    """
    Deposit funds into account (simulated).
    POST /api/account/deposit/
    Body: { amount, description }
    """
    user   = get_user_from_token(request)
    amount = Decimal(str(request.data.get('amount', 0)))
    desc   = request.data.get('description', 'Deposit')

    if amount <= 0:
        return Response({'error': 'Amount must be greater than 0.'},
                        status=status.HTTP_400_BAD_REQUEST)

    with db_transaction.atomic():
        user.balance += amount
        user.save(update_fields=['balance', 'updated_at'])

        ref = generate_reference()
        Transaction.objects.create(
            sender=None, receiver=user,
            amount=amount, transaction_type='DEPOSIT',
            description=desc, reference=ref, status='COMPLETED',
        )

    return Response({
        'message':     'Deposit successful.',
        'reference':   ref,
        'amount':      str(amount),
        'new_balance': str(user.balance),
        'currency':    'KES',
    }, status=status.HTTP_200_OK)