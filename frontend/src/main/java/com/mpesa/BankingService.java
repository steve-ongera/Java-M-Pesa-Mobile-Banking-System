package com.mpesa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * All banking operations: balance, send money, deposit, statement, profile.
 */
public class BankingService {

    // ──────────────────────────────────────────
    // PROFILE
    // ──────────────────────────────────────────

    public static void viewProfile() {
        try {
            JsonObject res = ApiClient.get("/account/profile/");
            int code = res.get("_status_code").getAsInt();

            if (code == 200) {
                System.out.println();
                System.out.println("┌─────────────────────────────────┐");
                System.out.println("│           MY PROFILE            │");
                System.out.println("├─────────────────────────────────┤");
                System.out.printf( "│  Name   : %-23s│%n", res.get("full_name").getAsString());
                System.out.printf( "│  Phone  : %-23s│%n", res.get("phone_number").getAsString());
                System.out.printf( "│  Joined : %-23s│%n",
                        res.get("created_at").getAsString().substring(0, 10));
                System.out.println("└─────────────────────────────────┘");
            } else {
                handleError(res);
            }
        } catch (Exception e) {
            System.out.println("❌ Error fetching profile: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // BALANCE
    // ──────────────────────────────────────────

    public static void checkBalance() {
        try {
            JsonObject res = ApiClient.get("/account/balance/");
            int code = res.get("_status_code").getAsInt();

            if (code == 200) {
                System.out.println();
                System.out.println("┌─────────────────────────────────┐");
                System.out.println("│          ACCOUNT BALANCE        │");
                System.out.println("├─────────────────────────────────┤");
                System.out.printf( "│  Account : %-22s│%n", res.get("account").getAsString());
                System.out.printf( "│  Balance : KES %-18s│%n", res.get("balance").getAsString());
                System.out.println("└─────────────────────────────────┘");
            } else {
                handleError(res);
            }
        } catch (Exception e) {
            System.out.println("❌ Error fetching balance: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // SEND MONEY
    // ──────────────────────────────────────────

    public static void sendMoney(String recipientPhone, double amount, String description) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("recipient_phone", recipientPhone);
            body.addProperty("amount",          amount);
            body.addProperty("description",     description);

            JsonObject res = ApiClient.post("/account/send/", body);
            int code = res.get("_status_code").getAsInt();

            if (code == 200) {
                System.out.println();
                System.out.println("┌─────────────────────────────────────┐");
                System.out.println("│        TRANSFER SUCCESSFUL          │");
                System.out.println("├─────────────────────────────────────┤");
                System.out.printf( "│  To         : %-23s│%n", res.get("recipient").getAsString());
                System.out.printf( "│  Amount     : KES %-19s│%n", res.get("amount").getAsString());
                System.out.printf( "│  Reference  : %-23s│%n", res.get("reference").getAsString());
                System.out.printf( "│  New Balance: KES %-19s│%n", res.get("new_balance").getAsString());
                System.out.println("└─────────────────────────────────────┘");
            } else {
                handleError(res);
            }
        } catch (Exception e) {
            System.out.println("❌ Error sending money: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // DEPOSIT
    // ──────────────────────────────────────────

    public static void deposit(double amount, String description) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("amount",      amount);
            body.addProperty("description", description);

            JsonObject res = ApiClient.post("/account/deposit/", body);
            int code = res.get("_status_code").getAsInt();

            if (code == 200) {
                System.out.println();
                System.out.println("┌─────────────────────────────────┐");
                System.out.println("│        DEPOSIT SUCCESSFUL       │");
                System.out.println("├─────────────────────────────────┤");
                System.out.printf( "│  Amount     : KES %-13s│%n", res.get("amount").getAsString());
                System.out.printf( "│  Reference  : %-19s│%n", res.get("reference").getAsString());
                System.out.printf( "│  New Balance: KES %-13s│%n", res.get("new_balance").getAsString());
                System.out.println("└─────────────────────────────────┘");
            } else {
                handleError(res);
            }
        } catch (Exception e) {
            System.out.println("❌ Error making deposit: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // STATEMENT
    // ──────────────────────────────────────────

    public static void viewStatement() {
        try {
            JsonObject res = ApiClient.get("/account/statement/");
            int code = res.get("_status_code").getAsInt();

            if (code != 200) { handleError(res); return; }

            JsonArray txns  = res.getAsJsonArray("transactions");
            String account  = res.get("account").getAsString();
            int    count    = res.get("count").getAsInt();

            System.out.println();
            System.out.println("┌──────────────────────────────────────────────────────────────┐");
            System.out.printf( "│  MINI STATEMENT — %-42s│%n", account);
            System.out.printf( "│  Showing %d transaction(s)%-35s│%n", count, "");
            System.out.println("├────────────┬─────────┬──────────────┬───────────┬────────────┤");
            System.out.println("│    DATE    │  TYPE   │    PARTY     │  AMOUNT   │  REF       │");
            System.out.println("├────────────┼─────────┼──────────────┼───────────┼────────────┤");

            if (count == 0) {
                System.out.println("│               No transactions found                          │");
            } else {
                for (JsonElement el : txns) {
                    JsonObject t = el.getAsJsonObject();

                    String date   = t.get("timestamp").getAsString().substring(0, 10);
                    String type   = t.get("transaction_type").getAsString();
                    String party  = truncate(t.get("party").getAsString(), 12);
                    String amount = "KES " + t.get("amount").getAsString();
                    String ref    = truncate(t.get("reference").getAsString(), 10);

                    System.out.printf("│ %-10s │ %-7s │ %-12s │ %-9s │ %-10s │%n",
                            date, type, party, amount, ref);
                }
            }
            System.out.println("└────────────┴─────────┴──────────────┴───────────┴────────────┘");

        } catch (Exception e) {
            System.out.println("❌ Error fetching statement: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────

    private static void handleError(JsonObject res) {
        if (res.has("error")) {
            System.out.println("❌ " + res.get("error").getAsString());
        } else if (res.has("detail")) {
            System.out.println("❌ " + res.get("detail").getAsString());
        } else {
            System.out.println("❌ Request failed (HTTP " + res.get("_status_code").getAsInt() + ")");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
