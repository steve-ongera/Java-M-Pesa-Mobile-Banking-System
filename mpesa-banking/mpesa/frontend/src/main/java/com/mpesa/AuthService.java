package com.mpesa;

import com.google.gson.JsonObject;

/**
 * Handles user registration, login, and logout.
 */
public class AuthService {

    private static String loggedInName  = null;
    private static String loggedInPhone = null;

    public static String getLoggedInName()  { return loggedInName;  }
    public static String getLoggedInPhone() { return loggedInPhone; }

    // ──────────────────────────────────────────
    // REGISTER
    // ──────────────────────────────────────────

    /**
     * Register a new user account.
     * @return true if registration succeeded
     */
    public static boolean register(String phone, String fullName, String pin) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("phone_number", phone);
            body.addProperty("full_name",    fullName);
            body.addProperty("pin",          pin);

            JsonObject response = ApiClient.post("/auth/register/", body);
            int code = response.get("_status_code").getAsInt();

            if (code == 201) {
                System.out.println("\n✅ Account created for " + fullName);
                System.out.println("   You can now log in with your phone and PIN.");
                return true;
            } else {
                printError(response);
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Connection error: " + e.getMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────
    // LOGIN
    // ──────────────────────────────────────────

    /**
     * Login with phone number and PIN.
     * Saves the JWT token in ApiClient on success.
     * @return true if login succeeded
     */
    public static boolean login(String phone, String pin) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("phone_number", phone);
            body.addProperty("pin",          pin);

            JsonObject response = ApiClient.post("/auth/login/", body);
            int code = response.get("_status_code").getAsInt();

            if (code == 200 && response.has("token")) {
                String token = response.get("token").getAsString();
                ApiClient.setToken(token);

                JsonObject user = response.getAsJsonObject("user");
                loggedInName  = user.get("full_name").getAsString();
                loggedInPhone = user.get("phone_number").getAsString();

                System.out.println("\n✅ Welcome back, " + loggedInName + "!");
                return true;
            } else {
                printError(response);
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Connection error: " + e.getMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────
    // LOGOUT
    // ──────────────────────────────────────────

    public static void logout() {
        try {
            ApiClient.post("/auth/logout/", new JsonObject());
        } catch (Exception ignored) {}

        ApiClient.clearToken();
        loggedInName  = null;
        loggedInPhone = null;
        System.out.println("\n👋 You have been logged out. Goodbye!");
    }

    // ──────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────

    private static void printError(JsonObject response) {
        if (response.has("error")) {
            System.out.println("❌ " + response.get("error").getAsString());
        } else if (response.has("detail")) {
            System.out.println("❌ " + response.get("detail").getAsString());
        } else {
            System.out.println("❌ Request failed (status "
                    + response.get("_status_code").getAsInt() + ")");
        }
    }
}
