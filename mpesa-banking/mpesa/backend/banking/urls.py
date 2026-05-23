from django.urls import path
from . import views

urlpatterns = [

    # ── AUTH ──────────────────────────────────
    path('auth/register/', views.register,  name='register'),
    path('auth/login/',    views.login,     name='login'),
    path('auth/logout/',   views.logout,    name='logout'),

    # ── ACCOUNT ───────────────────────────────
    path('account/profile/',   views.profile,    name='profile'),
    path('account/balance/',   views.balance,    name='balance'),
    path('account/send/',      views.send_money, name='send_money'),
    path('account/deposit/',   views.deposit,    name='deposit'),
    path('account/statement/', views.statement,  name='statement'),
]
