#  Java M-Pesa Mobile Banking System

A simple mobile banking system simulating M-Pesa functionality with:
- **Backend:** Django (Python) REST API + SQLite/PostgreSQL
- **Frontend:** Java Console Application (fetches data via API)
- **Auth:** Phone number + PIN login

---

##  Project Structure

```
mpesa-banking/
├── backend/                    # Django REST API
│   ├── manage.py
│   ├── requirements.txt
│   ├── mpesa_backend/
│   │   ├── __init__.py
│   │   ├── settings.py
│   │   ├── urls.py
│   │   └── wsgi.py
│   └── banking/
│       ├── __init__.py
│       ├── admin.py
│       ├── models.py           # User, Account, Transaction models
│       ├── serializers.py
│       ├── views.py            # API endpoints
│       └── urls.py
│
└── frontend/                   # Java Console App
    ├── pom.xml                 # Maven config
    └── src/main/java/com/mpesa/
        ├── Main.java           # Entry point
        ├── ApiClient.java      # HTTP helper
        ├── AuthService.java    # Login/logout
        ├── BankingService.java # Balance, send money, statement
        └── models/
            ├── User.java
            └── Transaction.java
```

---

##  Prerequisites

| Tool | Version |
|------|---------|
| Python | 3.10+ |
| Django | 4.2+ |
| Java JDK | 17+ |
| Maven | 3.8+ |
| pip | latest |

---

## 🚀 Backend Setup (Django)

### 1. Clone and set up virtual environment

```bash
git clone https://github.com/yourname/mpesa-banking.git
cd mpesa-banking/backend

python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. `requirements.txt`

```
Django==4.2
djangorestframework==3.15
djangorestframework-simplejwt==5.3
django-cors-headers==4.3
```

### 3. Run migrations and start server

```bash
python manage.py makemigrations
python manage.py migrate
python manage.py createsuperuser   # optional admin access
python manage.py runserver 0.0.0.0:8000
```

Server runs at: `http://127.0.0.1:8000`

---

## 🗄️ Django Models (`banking/models.py`)

```python
from django.db import models
import uuid

class UserAccount(models.Model):
    id           = models.UUIDField(primary_key=True, default=uuid.uuid4)
    phone_number = models.CharField(max_length=15, unique=True)
    pin_hash     = models.CharField(max_length=255)            # store bcrypt hash
    full_name    = models.CharField(max_length=100)
    balance      = models.DecimalField(max_digits=12, decimal_places=2, default=0.00)
    created_at   = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.full_name} ({self.phone_number})"

class Transaction(models.Model):
    TYPES = [('SEND','Send Money'),('RECEIVE','Receive Money'),
             ('DEPOSIT','Deposit'),('WITHDRAW','Withdraw')]

    id              = models.UUIDField(primary_key=True, default=uuid.uuid4)
    sender          = models.ForeignKey(UserAccount, on_delete=models.SET_NULL,
                                        null=True, related_name='sent')
    receiver        = models.ForeignKey(UserAccount, on_delete=models.SET_NULL,
                                        null=True, related_name='received')
    amount          = models.DecimalField(max_digits=12, decimal_places=2)
    transaction_type= models.CharField(max_length=10, choices=TYPES)
    description     = models.TextField(blank=True)
    timestamp       = models.DateTimeField(auto_now_add=True)
    reference       = models.CharField(max_length=20, unique=True)

    def __str__(self):
        return f"{self.transaction_type} KES {self.amount} @ {self.timestamp}"
```

---

## 🔌 API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register/` | Register new user |
| POST | `/api/auth/login/` | Login with phone + PIN → returns JWT |
| POST | `/api/auth/logout/` | Invalidate token |

### Banking

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/account/balance/` | Get current balance | ✅ |
| POST | `/api/account/send/` | Send money | ✅ |
| GET | `/api/account/statement/` | Transaction history | ✅ |
| GET | `/api/account/profile/` | User profile | ✅ |

### Request / Response Examples

**POST `/api/auth/login/`**
```json
// Request
{
  "phone_number": "0712345678",
  "pin": "1234"
}

// Response 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "uuid-here",
    "full_name": "John Doe",
    "phone_number": "0712345678"
  }
}
```

**GET `/api/account/balance/`**
```json
// Response 200 OK
{
  "balance": "5420.00",
  "currency": "KES"
}
```

**POST `/api/account/send/`**
```json
// Request
{
  "recipient_phone": "0798765432",
  "amount": 500,
  "description": "Rent payment"
}

// Response 200 OK
{
  "reference": "MP-20240523-0001",
  "amount": "500.00",
  "recipient": "Jane Wanjiku",
  "new_balance": "4920.00",
  "timestamp": "2024-05-23T10:30:00Z"
}
```

**GET `/api/account/statement/`**
```json
// Response 200 OK
{
  "transactions": [
    {
      "id": "uuid",
      "type": "SEND",
      "amount": "500.00",
      "party": "Jane Wanjiku",
      "reference": "MP-20240523-0001",
      "description": "Rent payment",
      "timestamp": "2024-05-23T10:30:00Z"
    }
  ]
}
```

---

## 📡 Django Views (`banking/views.py`) — Key Snippet

```python
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
```

---

## ☕ Java Console Frontend Setup

### 1. Dependencies (`pom.xml`)

```xml
<dependencies>
  <!-- HTTP client -->
  <dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
  </dependency>
  <!-- JSON parsing -->
  <dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
  </dependency>
</dependencies>
```

### 2. `ApiClient.java`

```java
package com.mpesa;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;

public class ApiClient {
    private static final String BASE_URL = "http://127.0.0.1:8000/api";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static String authToken = null;

    public static void setToken(String token) { authToken = token; }
    public static String getToken()           { return authToken;  }

    public static JsonObject post(String endpoint, JsonObject body) throws IOException {
        Request.Builder req = new Request.Builder()
            .url(BASE_URL + endpoint)
            .post(RequestBody.create(body.toString(),
                  MediaType.get("application/json")));
        if (authToken != null)
            req.addHeader("Authorization", "Bearer " + authToken);

        try (Response resp = client.newCall(req.build()).execute()) {
            String json = resp.body().string();
            return gson.fromJson(json, JsonObject.class);
        }
    }

    public static JsonObject get(String endpoint) throws IOException {
        Request req = new Request.Builder()
            .url(BASE_URL + endpoint)
            .addHeader("Authorization", "Bearer " + authToken)
            .build();

        try (Response resp = client.newCall(req.build()).execute()) {
            String json = resp.body().string();
            return gson.fromJson(json, JsonObject.class);
        }
    }
}
```

### 3. `AuthService.java`

```java
package com.mpesa;

import com.google.gson.JsonObject;

public class AuthService {

    public static boolean login(String phone, String pin) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("phone_number", phone);
            body.addProperty("pin", pin);

            JsonObject response = ApiClient.post("/auth/login/", body);
            if (response.has("token")) {
                ApiClient.setToken(response.get("token").getAsString());
                String name = response.getAsJsonObject("user")
                                      .get("full_name").getAsString();
                System.out.println("✅ Welcome, " + name + "!");
                return true;
            } else {
                System.out.println("❌ " + response.get("error").getAsString());
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Connection error: " + e.getMessage());
            return false;
        }
    }

    public static void logout() {
        ApiClient.setToken(null);
        System.out.println("👋 Logged out successfully.");
    }
}
```

### 4. `BankingService.java`

```java
package com.mpesa;

import com.google.gson.*;

public class BankingService {

    public static void checkBalance() {
        try {
            JsonObject res = ApiClient.get("/account/balance/");
            System.out.println("\n💰 Balance: KES " + res.get("balance").getAsString());
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    public static void sendMoney(String recipientPhone, double amount, String desc) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("recipient_phone", recipientPhone);
            body.addProperty("amount", amount);
            body.addProperty("description", desc);

            JsonObject res = ApiClient.post("/account/send/", body);
            if (res.has("reference")) {
                System.out.println("\n✅ Sent KES " + amount
                    + " to " + res.get("recipient").getAsString());
                System.out.println("   Reference : " + res.get("reference").getAsString());
                System.out.println("   New balance: KES " + res.get("new_balance").getAsString());
            } else {
                System.out.println("❌ " + res.get("error").getAsString());
            }
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    public static void viewStatement() {
        try {
            JsonObject res = ApiClient.get("/account/statement/");
            JsonArray txns = res.getAsJsonArray("transactions");
            System.out.println("\n📋 --- MINI STATEMENT ---");
            for (JsonElement el : txns) {
                JsonObject t = el.getAsJsonObject();
                System.out.printf("  [%s] %s KES %s | %s | Ref: %s%n",
                    t.get("timestamp").getAsString().substring(0, 10),
                    t.get("type").getAsString(),
                    t.get("amount").getAsString(),
                    t.get("party").getAsString(),
                    t.get("reference").getAsString()
                );
            }
            System.out.println("------------------------");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }
}
```

### 5. `Main.java` — Console Menu

```java
package com.mpesa;

import java.util.Scanner;

public class Main {
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("================================");
        System.out.println("   📲 M-PESA BANKING SYSTEM    ");
        System.out.println("================================");

        if (!doLogin()) return;

        boolean running = true;
        while (running) {
            System.out.println("\n--- MAIN MENU ---");
            System.out.println("1. Check Balance");
            System.out.println("2. Send Money");
            System.out.println("3. View Statement");
            System.out.println("4. Logout");
            System.out.print("Select option: ");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> BankingService.checkBalance();
                case "2" -> {
                    System.out.print("Recipient phone: ");
                    String phone = sc.nextLine().trim();
                    System.out.print("Amount (KES): ");
                    double amount = Double.parseDouble(sc.nextLine().trim());
                    System.out.print("Description: ");
                    String desc = sc.nextLine().trim();
                    BankingService.sendMoney(phone, amount, desc);
                }
                case "3" -> BankingService.viewStatement();
                case "4" -> { AuthService.logout(); running = false; }
                default  -> System.out.println("❌ Invalid option.");
            }
        }
    }

    private static boolean doLogin() {
        int attempts = 0;
        while (attempts < 3) {
            System.out.print("\nPhone number: ");
            String phone = sc.nextLine().trim();
            System.out.print("PIN: ");
            String pin = sc.nextLine().trim();
            if (AuthService.login(phone, pin)) return true;
            attempts++;
            System.out.println("Attempts remaining: " + (3 - attempts));
        }
        System.out.println("❌ Too many failed attempts. Exiting.");
        return false;
    }
}
```

---

## 🖥️ Running the Java Frontend

```bash
cd frontend
mvn clean package -q
java -jar target/mpesa-console-1.0.jar
```

**Expected console output:**

```
================================
   📲 M-PESA BANKING SYSTEM
================================

Phone number: 0712345678
PIN: ****
✅ Welcome, John Doe!

--- MAIN MENU ---
1. Check Balance
2. Send Money
3. View Statement
4. Logout
Select option: 1

💰 Balance: KES 5420.00
```

---

## 🔐 Security Notes

- Pins are stored as **bcrypt hashes** — never plain text
- All banking endpoints require a **JWT Bearer token**
- Tokens expire after **60 minutes** (configurable in Django settings)
- PIN entry should use `Console.readPassword()` in production to hide input

```java
// Production: hide PIN input
char[] pinChars = System.console().readPassword("PIN: ");
String pin = new String(pinChars);
java.util.Arrays.fill(pinChars, ' '); // clear from memory
```

---

## 🌍 Django Settings — Key Config

```python
# settings.py

INSTALLED_APPS = [
    ...
    'rest_framework',
    'rest_framework_simplejwt',
    'corsheaders',
    'banking',
]

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    ...
]

CORS_ALLOW_ALL_ORIGINS = True  # restrict in production

REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': [
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    ]
}

from datetime import timedelta
SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(minutes=60),
}
```

---

## 🧪 Quick Test with curl

```bash
# Register
curl -X POST http://localhost:8000/api/auth/register/ \
  -H "Content-Type: application/json" \
  -d '{"phone_number":"0712345678","pin":"1234","full_name":"John Doe"}'

# Login
curl -X POST http://localhost:8000/api/auth/login/ \
  -H "Content-Type: application/json" \
  -d '{"phone_number":"0712345678","pin":"1234"}'

# Check balance (replace TOKEN)
curl http://localhost:8000/api/account/balance/ \
  -H "Authorization: Bearer TOKEN"
```

---

## 📌 Future Improvements

- Add M-Pesa STK Push integration (Safaricom Daraja API)
- Add SMS notifications via Africa's Talking API
- Swap SQLite for PostgreSQL in production
- Add transaction limits and daily caps
- Build Android/iOS app using the same Django API

---

## 📄 License

MIT License — free to use and extend for learning purposes.