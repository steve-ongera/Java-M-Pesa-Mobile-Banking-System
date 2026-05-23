from django.contrib import admin
from django.urls import path, include
from rest_framework_simplejwt.views import TokenRefreshView

urlpatterns = [
    # Django admin
    path('admin/', admin.site.urls),

    # Banking API — all endpoints live under /api/
    path('api/', include('banking.urls')),

    # JWT token refresh (optional — for long-lived sessions)
    path('api/token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
]
