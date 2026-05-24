import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * M-Pesa Banking System — Single File Console App
 *
 * Compile:  javac MpesaBank.java
 * Run:      java MpesaBank
 *
 * Requires: Java 11+  |  Django backend running on http://127.0.0.1:8000
 */
public class MpesaBank {

    // ── CONFIG ────────────────────────────────────────────────────────────
    static final String BASE_URL = "http://127.0.0.1:8000/api";

    // ── STATE ─────────────────────────────────────────────────────────────
    static String token        = null;
    static String loggedInName = null;
    static Scanner sc          = new Scanner(System.in);
    static HttpClient http     = HttpClient.newBuilder()
                                    .connectTimeout(Duration.ofSeconds(10))
                                    .build();

    // ══════════════════════════════════════════════════════════════════════
    // MAIN
    // ══════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        splash();

        // Auth loop
        while (token == null) {
            System.out.println("  1. Login");
            System.out.println("  2. Create Account");
            System.out.println("  3. Exit");
            System.out.print("\n  Choose: ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1" -> login();
                case "2" -> register();
                case "3" -> { System.out.println("\nGoodbye! 👋"); return; }
                default  -> System.out.println("  Invalid option.\n");
            }
        }

        // Banking loop
        while (token != null) {
            System.out.println();
            System.out.println("════════════════════════════════════════");
            System.out.println("  Hello, " + loggedInName + " 👋");
            System.out.println("────────────────────────────────────────");
            System.out.println("  1. Check Balance");
            System.out.println("  2. Send Money");
            System.out.println("  3. Deposit Funds");
            System.out.println("  4. Mini Statement");
            System.out.println("  5. My Profile");
            System.out.println("  6. Logout");
            System.out.println("════════════════════════════════════════");
            System.out.print("  Select: ");

            switch (sc.nextLine().trim()) {
                case "1" -> checkBalance();
                case "2" -> sendMoney();
                case "3" -> deposit();
                case "4" -> statement();
                case "5" -> profile();
                case "6" -> logout();
                default  -> System.out.println("  Invalid option. Choose 1-6.");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUTH
    // ══════════════════════════════════════════════════════════════════════

    static void login() {
        System.out.println("\n── LOGIN ────────────────────────────────");
        System.out.print("  Phone : ");
        String phone = sc.nextLine().trim();
        System.out.print("  PIN   : ");
        String pin = sc.nextLine().trim();

        String body = "{\"phone_number\":\"" + phone + "\",\"pin\":\"" + pin + "\"}";
        Map<String, Object> res = post("/auth/login/", body);

        if (res.containsKey("token")) {
            token        = str(res, "token");
            Map<String, Object> user = obj(res, "user");
            loggedInName = str(user, "full_name");
            System.out.println("\n  ✅ Welcome back, " + loggedInName + "!");
        } else {
            System.out.println("  ❌ " + error(res));
        }
    }

    static void register() {
        System.out.println("\n── CREATE ACCOUNT ───────────────────────");
        System.out.print("  Full name : ");
        String name = sc.nextLine().trim();
        System.out.print("  Phone     : ");
        String phone = sc.nextLine().trim();
        System.out.print("  PIN       : ");
        String pin = sc.nextLine().trim();
        System.out.print("  Confirm   : ");
        String confirm = sc.nextLine().trim();

        if (!pin.equals(confirm)) {
            System.out.println("  ❌ PINs do not match.");
            return;
        }

        String body = "{\"phone_number\":\"" + phone + "\","
                    + "\"full_name\":\"" + name + "\","
                    + "\"pin\":\"" + pin + "\"}";
        Map<String, Object> res = post("/auth/register/", body);

        if (res.containsKey("message") || res.containsKey("user")) {
            System.out.println("  ✅ Account created! You can now log in.");
        } else {
            System.out.println("  ❌ " + error(res));
        }
        System.out.println();
    }

    static void logout() {
        post("/auth/logout/", "{}");
        token        = null;
        loggedInName = null;
        System.out.println("\n  👋 Logged out successfully.");
    }

    // ══════════════════════════════════════════════════════════════════════
    // BANKING
    // ══════════════════════════════════════════════════════════════════════

    static void checkBalance() {
        Map<String, Object> res = get("/account/balance/");
        if (res.containsKey("balance")) {
            System.out.println();
            System.out.println("  ┌─────────────────────────────┐");
            System.out.println("  │       ACCOUNT BALANCE       │");
            System.out.println("  ├─────────────────────────────┤");
            System.out.printf ("  │  Account : %-17s│%n", str(res, "account"));
            System.out.printf ("  │  Balance : KES %-13s│%n", str(res, "balance"));
            System.out.println("  └─────────────────────────────┘");
        } else {
            System.out.println("  ❌ " + error(res));
        }
    }

    static void sendMoney() {
        System.out.println("\n── SEND MONEY ───────────────────────────");
        System.out.print("  Recipient phone : ");
        String phone = sc.nextLine().trim();
        System.out.print("  Amount (KES)    : ");
        String amount = sc.nextLine().trim();
        System.out.print("  Description     : ");
        String desc = sc.nextLine().trim();

        System.out.printf("%n  Confirm sending KES %s to %s? (y/n): ", amount, phone);
        if (!sc.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("  Cancelled.");
            return;
        }

        String body = "{\"recipient_phone\":\"" + phone + "\","
                    + "\"amount\":" + amount + ","
                    + "\"description\":\"" + desc + "\"}";
        Map<String, Object> res = post("/account/send/", body);

        if (res.containsKey("reference")) {
            System.out.println();
            System.out.println("  ┌──────────────────────────────────┐");
            System.out.println("  │      TRANSFER SUCCESSFUL ✅      │");
            System.out.println("  ├──────────────────────────────────┤");
            System.out.printf ("  │  To         : %-18s│%n", str(res, "recipient"));
            System.out.printf ("  │  Amount     : KES %-13s│%n", str(res, "amount"));
            System.out.printf ("  │  Reference  : %-18s│%n", str(res, "reference"));
            System.out.printf ("  │  New Balance: KES %-13s│%n", str(res, "new_balance"));
            System.out.println("  └──────────────────────────────────┘");
        } else {
            System.out.println("  ❌ " + error(res));
        }
    }

    static void deposit() {
        System.out.println("\n── DEPOSIT FUNDS ────────────────────────");
        System.out.print("  Amount (KES) : ");
        String amount = sc.nextLine().trim();
        System.out.print("  Description  : ");
        String desc = sc.nextLine().trim();

        String body = "{\"amount\":" + amount + ",\"description\":\"" + desc + "\"}";
        Map<String, Object> res = post("/account/deposit/", body);

        if (res.containsKey("reference")) {
            System.out.println();
            System.out.println("  ┌─────────────────────────────┐");
            System.out.println("  │    DEPOSIT SUCCESSFUL ✅    │");
            System.out.println("  ├─────────────────────────────┤");
            System.out.printf ("  │  Amount     : KES %-9s│%n", str(res, "amount"));
            System.out.printf ("  │  Reference  : %-15s│%n", str(res, "reference"));
            System.out.printf ("  │  New Balance: KES %-9s│%n", str(res, "new_balance"));
            System.out.println("  └─────────────────────────────┘");
        } else {
            System.out.println("  ❌ " + error(res));
        }
    }

    static void statement() {
        Map<String, Object> res = get("/account/statement/");

        if (!res.containsKey("transactions")) {
            System.out.println("  ❌ " + error(res));
            return;
        }

        List<Object> txns = list(res, "transactions");
        System.out.println();
        System.out.println("  MINI STATEMENT — " + str(res, "account"));
        System.out.println("  ──────────────────────────────────────────────────────────");
        System.out.printf ("  %-12s %-10s %-16s %-12s %-14s%n",
                           "DATE", "TYPE", "PARTY", "AMOUNT", "REFERENCE");
        System.out.println("  ──────────────────────────────────────────────────────────");

        if (txns.isEmpty()) {
            System.out.println("  No transactions found.");
        } else {
            for (Object o : txns) {
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) o;

                String date  = str(t, "timestamp");
                if (date.length() > 10) date = date.substring(0, 10);

                String type  = trunc(str(t, "transaction_type"), 10);
                String party = trunc(str(t, "party"), 15);
                String amt   = "KES " + str(t, "amount");
                String ref   = str(t, "reference");

                System.out.printf("  %-12s %-10s %-16s %-12s %-14s%n",
                                  date, type, party, amt, ref);
            }
        }
        System.out.println("  ──────────────────────────────────────────────────────────");
    }

    static void profile() {
        Map<String, Object> res = get("/account/profile/");
        if (res.containsKey("full_name")) {
            String joined = str(res, "created_at");
            if (joined.length() > 10) joined = joined.substring(0, 10);
            System.out.println();
            System.out.println("  ┌─────────────────────────────┐");
            System.out.println("  │          MY PROFILE         │");
            System.out.println("  ├─────────────────────────────┤");
            System.out.printf ("  │  Name   : %-19s│%n", str(res, "full_name"));
            System.out.printf ("  │  Phone  : %-19s│%n", str(res, "phone_number"));
            System.out.printf ("  │  Joined : %-19s│%n", joined);
            System.out.println("  └─────────────────────────────┘");
        } else {
            System.out.println("  ❌ " + error(res));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HTTP HELPERS
    // ══════════════════════════════════════════════════════════════════════

    static Map<String, Object> post(String endpoint, String jsonBody) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (token != null)
                req.header("Authorization", "Bearer " + token);

            HttpResponse<String> resp = http.send(req.build(),
                                        HttpResponse.BodyHandlers.ofString());
            return parseJson(resp.body());

        } catch (ConnectException e) {
            return Map.of("error",
                "Cannot reach server. Is Django running? (python manage.py runserver)");
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    static Map<String, Object> get(String endpoint) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET();

            if (token != null)
                req.header("Authorization", "Bearer " + token);

            HttpResponse<String> resp = http.send(req.build(),
                                        HttpResponse.BodyHandlers.ofString());
            return parseJson(resp.body());

        } catch (ConnectException e) {
            return Map.of("error",
                "Cannot reach server. Is Django running? (python manage.py runserver)");
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MINI JSON PARSER  (no external libraries)
    // ══════════════════════════════════════════════════════════════════════

    static int jpos;
    static String jsrc;

    static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            jsrc = json.trim();
            jpos = 0;
            Object v = jsonValue();
            if (v instanceof Map<?,?> m) {
                @SuppressWarnings("unchecked") Map<String,Object> r = (Map<String,Object>) m;
                return r;
            }
        } catch (Exception ignored) {}
        return new HashMap<>();
    }

    static Object jsonValue() {
        jsonSkip();
        if (jpos >= jsrc.length()) return null;
        char c = jsrc.charAt(jpos);
        if (c == '{') return jsonObj();
        if (c == '[') return jsonArr();
        if (c == '"') return jsonStr();
        if (c == 't') { jpos += 4; return Boolean.TRUE; }
        if (c == 'f') { jpos += 5; return Boolean.FALSE; }
        if (c == 'n') { jpos += 4; return null; }
        return jsonNum();
    }

    static Map<String, Object> jsonObj() {
        Map<String, Object> m = new LinkedHashMap<>();
        jpos++; jsonSkip();
        while (jpos < jsrc.length() && jsrc.charAt(jpos) != '}') {
            jsonSkip();
            String key = jsonStr();
            jsonSkip(); jpos++; jsonSkip(); // colon
            m.put(key, jsonValue());
            jsonSkip();
            if (jpos < jsrc.length() && jsrc.charAt(jpos) == ',') jpos++;
            jsonSkip();
        }
        if (jpos < jsrc.length()) jpos++;
        return m;
    }

    static List<Object> jsonArr() {
        List<Object> lst = new ArrayList<>();
        jpos++; jsonSkip();
        while (jpos < jsrc.length() && jsrc.charAt(jpos) != ']') {
            lst.add(jsonValue());
            jsonSkip();
            if (jpos < jsrc.length() && jsrc.charAt(jpos) == ',') jpos++;
            jsonSkip();
        }
        if (jpos < jsrc.length()) jpos++;
        return lst;
    }

    static String jsonStr() {
        jpos++; // opening "
        StringBuilder sb = new StringBuilder();
        while (jpos < jsrc.length()) {
            char c = jsrc.charAt(jpos++);
            if (c == '"') break;
            if (c == '\\' && jpos < jsrc.length()) {
                char e = jsrc.charAt(jpos++);
                switch (e) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    default   -> sb.append(e);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    static Number jsonNum() {
        int s = jpos;
        while (jpos < jsrc.length() && "0123456789.-eE+".indexOf(jsrc.charAt(jpos)) >= 0) jpos++;
        String n = jsrc.substring(s, jpos);
        try { return Long.parseLong(n); }
        catch (Exception e) { return Double.parseDouble(n); }
    }

    static void jsonSkip() {
        while (jpos < jsrc.length() && Character.isWhitespace(jsrc.charAt(jpos))) jpos++;
    }

    // ══════════════════════════════════════════════════════════════════════
    // SMALL UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Map<?,?> mp) ? (Map<String, Object>) mp : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof List<?> l) ? (List<Object>) l : new ArrayList<>();
    }

    static String error(Map<String, Object> m) {
        if (m.containsKey("error"))  return str(m, "error");
        if (m.containsKey("detail")) return str(m, "detail");
        return "Something went wrong.";
    }

    static String trunc(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    static void splash() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║                                          ║");
        System.out.println("║      📱  M-PESA BANKING SYSTEM  📱      ║");
        System.out.println("║          Powered by Django REST          ║");
        System.out.println("║                                          ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();
    }
}