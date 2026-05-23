import uuid
import datetime
from rest_framework_simplejwt.tokens import AccessToken
from .models import UserAccount


def generate_reference():
    """Generate a unique M-Pesa style transaction reference."""
    today = datetime.datetime.now().strftime('%Y%m%d')
    short = uuid.uuid4().hex[:6].upper()
    return f"MP{today}{short}"


def token_for_user(user):
    """Generate a JWT access token for the given UserAccount."""
    token = AccessToken()
    token['user_id']      = str(user.id)
    token['phone_number'] = user.phone_number
    token['full_name']    = user.full_name
    return str(token)


def get_user_from_token(request):
    """Extract the UserAccount from the JWT token in the request."""
    try:
        user_id = request.auth.get('user_id')
        return UserAccount.objects.get(id=user_id)
    except (UserAccount.DoesNotExist, AttributeError, Exception):
        return None
