import java.io.Console;
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
 * Requires: Java 11+  |  Django running on http://127.0.0.1:8000
 */
public class MpesaBank {

    // ── CONFIG ────────────────────────────────────────────────────────────
    static final String BASE_URL = "http://127.0.0.1:8000/api";

    // ── STATE ─────────────────────────────────────────────────────────────
    static String     token        = null;
    static String     loggedInName = null;
    static Scanner    sc           = new Scanner(System.in);
    static HttpClient http         = HttpClient.newBuilder()
                                        .connectTimeout(Duration.ofSeconds(10))
                                        .build();

    // ══════════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        splash();

        // ── Auth loop ─────────────────────────────────────────────────────
        while (token == null) {
            System.out.println("  1. Login");
            System.out.println("  2. Create Account");
            System.out.println("  3. Exit");
            System.out.print("\n  Choose: ");

            switch (sc.nextLine().trim()) {
                case "1" -> login();
                case "2" -> register();
                case "3" -> { System.out.println("\nGoodbye! 👋\n"); return; }
                default  -> System.out.println("  ❌ Invalid option.\n");
            }
        }

        // ── Banking loop ──────────────────────────────────────────────────
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
                default  -> System.out.println("  ❌ Choose 1–6.");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUTH
    // ══════════════════════════════════════════════════════════════════════

    static void login() {
        System.out.println("\n── LOGIN ────────────────────────────────");
        System.out.print("  Phone number : ");
        String phone = sc.nextLine().trim();
        System.out.print("  PIN          : ");
        String pin = readPin();

        String body = json("phone_number", phone, "pin", pin);
        Map<String, Object> res = post("/auth/login/", body);

        if (res.containsKey("token")) {
            token        = str(res, "token");
            loggedInName = str(obj(res, "user"), "full_name");
            System.out.println("\n  ✅ Welcome back, " + loggedInName + "!");
        } else {
            System.out.println("  ❌ " + error(res));
            System.out.println();
        }
    }

    static void register() {
        System.out.println("\n── CREATE ACCOUNT ───────────────────────");
        System.out.print("  Full name    : ");
        String name = sc.nextLine().trim();
        System.out.print("  Phone number : ");
        String phone = sc.nextLine().trim();
        System.out.print("  Choose PIN   : ");
        String pin = readPin();
        System.out.print("  Confirm PIN  : ");
        String confirm = readPin();

        if (!pin.equals(confirm)) {
            System.out.println("  ❌ PINs do not match. Try again.\n");
            return;
        }

        String body = json("phone_number", phone, "full_name", name, "pin", pin);
        Map<String, Object> res = post("/auth/register/", body);

        if (res.containsKey("message") || res.containsKey("user")) {
            System.out.println("  ✅ Account created! You can now log in.\n");
        } else {
            System.out.println("  ❌ " + error(res) + "\n");
        }
    }

    static void logout() {
        post("/auth/logout/", "{}");
        token        = null;
        loggedInName = null;
        System.out.println("\n  👋 Logged out. Goodbye!\n");
    }

    // ══════════════════════════════════════════════════════════════════════
    // BANKING
    // ══════════════════════════════════════════════════════════════════════

    static void checkBalance() {
        Map<String, Object> res = get("/account/balance/");
        if (!res.containsKey("balance")) { System.out.println("  ❌ " + error(res)); return; }

        System.out.println();
        System.out.println("  ┌─────────────────────────────────┐");
        System.out.println("  │         ACCOUNT BALANCE         │");
        System.out.println("  ├─────────────────────────────────┤");
        System.out.printf ("  │  Account : %-21s│%n", str(res, "account"));
        System.out.printf ("  │  Balance : KES %-17s│%n", str(res, "balance"));
        System.out.println("  └─────────────────────────────────┘");
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
            System.out.println("  Transfer cancelled.");
            return;
        }

        // Build JSON manually so amount stays a number (not quoted)
        String body = "{\"recipient_phone\":\"" + esc(phone)  + "\","
                    + "\"amount\":"              + amount      + ","
                    + "\"description\":\""       + esc(desc)   + "\"}";

        Map<String, Object> res = post("/account/send/", body);

        if (res.containsKey("reference")) {
            System.out.println();
            System.out.println("  ┌──────────────────────────────────────┐");
            System.out.println("  │        TRANSFER SUCCESSFUL ✅        │");
            System.out.println("  ├──────────────────────────────────────┤");
            System.out.printf ("  │  To          : %-22s│%n", str(res, "recipient"));
            System.out.printf ("  │  Amount      : KES %-17s│%n", str(res, "amount"));
            System.out.printf ("  │  Reference   : %-22s│%n", str(res, "reference"));
            System.out.printf ("  │  New Balance : KES %-17s│%n", str(res, "new_balance"));
            System.out.println("  └──────────────────────────────────────┘");
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

        String body = "{\"amount\":"        + amount    + ","
                    + "\"description\":\"" + esc(desc) + "\"}";

        Map<String, Object> res = post("/account/deposit/", body);

        if (res.containsKey("reference")) {
            System.out.println();
            System.out.println("  ┌─────────────────────────────────┐");
            System.out.println("  │      DEPOSIT SUCCESSFUL ✅      │");
            System.out.println("  ├─────────────────────────────────┤");
            System.out.printf ("  │  Amount      : KES %-11s│%n", str(res, "amount"));
            System.out.printf ("  │  Reference   : %-17s│%n", str(res, "reference"));
            System.out.printf ("  │  New Balance : KES %-11s│%n", str(res, "new_balance"));
            System.out.println("  └─────────────────────────────────┘");
        } else {
            System.out.println("  ❌ " + error(res));
        }
    }

    static void statement() {
        Map<String, Object> res = get("/account/statement/");
        if (!res.containsKey("transactions")) { System.out.println("  ❌ " + error(res)); return; }

        List<Object> txns   = list(res, "transactions");
        String       acct   = str(res, "account");
        int          count  = txns.size();

        System.out.println();
        System.out.println("  ════════════════════════════════════════════════════════════");
        System.out.println("   MINI STATEMENT — " + acct);
        System.out.printf ("   Showing %d transaction(s)%n", count);
        System.out.println("  ════════════════════════════════════════════════════════════");
        System.out.printf ("   %-12s  %-10s  %-16s  %-13s  %-16s%n",
                           "DATE", "TYPE", "PARTY", "AMOUNT", "REFERENCE");
        System.out.println("  ────────────────────────────────────────────────────────────");

        if (count == 0) {
            System.out.println("   No transactions found.");
        } else {
            for (Object o : txns) {
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) o;

                String date  = str(t, "timestamp");
                if (date.length() > 10) date = date.substring(0, 10);

                String type  = pad(str(t, "transaction_type"), 10);
                String party = pad(str(t, "party"), 16);
                String amt   = "KES " + str(t, "amount");
                String ref   = str(t, "reference");

                System.out.printf("   %-12s  %-10s  %-16s  %-13s  %-16s%n",
                                   date, type, party, amt, ref);
            }
        }
        System.out.println("  ════════════════════════════════════════════════════════════");
    }

    static void profile() {
        Map<String, Object> res = get("/account/profile/");
        if (!res.containsKey("full_name")) { System.out.println("  ❌ " + error(res)); return; }

        String joined = str(res, "created_at");
        if (joined.length() > 10) joined = joined.substring(0, 10);

        System.out.println();
        System.out.println("  ┌─────────────────────────────────┐");
        System.out.println("  │           MY PROFILE            │");
        System.out.println("  ├─────────────────────────────────┤");
        System.out.printf ("  │  Name    : %-21s│%n", str(res, "full_name"));
        System.out.printf ("  │  Phone   : %-21s│%n", str(res, "phone_number"));
        System.out.printf ("  │  Balance : KES %-17s│%n", str(res, "balance"));
        System.out.printf ("  │  Joined  : %-21s│%n", joined);
        System.out.println("  └─────────────────────────────────┘");
    }

    // ══════════════════════════════════════════════════════════════════════
    // HTTP  (java.net.http — zero external dependencies)
    // ══════════════════════════════════════════════════════════════════════

    static Map<String, Object> post(String endpoint, String jsonBody) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            if (token != null) req.header("Authorization", "Bearer " + token);

            HttpResponse<String> resp = http.send(req.build(),
                                         HttpResponse.BodyHandlers.ofString());
            return parseJson(resp.body());

        } catch (ConnectException e) {
            System.out.println("\n  ⚠  Cannot connect — is Django running on port 8000?");
            return Map.of("error", "Server unreachable");
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
            if (token != null) req.header("Authorization", "Bearer " + token);

            HttpResponse<String> resp = http.send(req.build(),
                                         HttpResponse.BodyHandlers.ofString());
            return parseJson(resp.body());

        } catch (ConnectException e) {
            System.out.println("\n  ⚠  Cannot connect — is Django running on port 8000?");
            return Map.of("error", "Server unreachable");
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ZERO-DEPENDENCY JSON PARSER
    // ══════════════════════════════════════════════════════════════════════

    static int    jp;   // parser position (global — single-threaded)
    static String js;   // JSON source string

    static Map<String, Object> parseJson(String raw) {
        if (raw == null || raw.isBlank()) return new HashMap<>();
        try {
            js = raw.trim(); jp = 0;
            Object v = jVal();
            if (v instanceof Map<?,?> m) {
                @SuppressWarnings("unchecked") Map<String,Object> r = (Map<String,Object>) m;
                return r;
            }
        } catch (Exception ignored) {}
        return new HashMap<>();
    }

    static Object jVal() {
        jSkip();
        if (jp >= js.length()) return null;
        char c = js.charAt(jp);
        return switch (c) {
            case '{' -> jObj();
            case '[' -> jArr();
            case '"' -> jStr();
            case 't' -> { jp += 4; yield Boolean.TRUE; }
            case 'f' -> { jp += 5; yield Boolean.FALSE; }
            case 'n' -> { jp += 4; yield null; }
            default  -> jNum();
        };
    }

    static Map<String, Object> jObj() {
        Map<String, Object> m = new LinkedHashMap<>();
        jp++; jSkip();
        while (jp < js.length() && js.charAt(jp) != '}') {
            jSkip();
            String k = jStr(); jSkip(); jp++; jSkip();  // key : skip colon
            m.put(k, jVal());
            jSkip();
            if (jp < js.length() && js.charAt(jp) == ',') jp++;
            jSkip();
        }
        if (jp < js.length()) jp++;
        return m;
    }

    static List<Object> jArr() {
        List<Object> l = new ArrayList<>();
        jp++; jSkip();
        while (jp < js.length() && js.charAt(jp) != ']') {
            l.add(jVal()); jSkip();
            if (jp < js.length() && js.charAt(jp) == ',') jp++;
            jSkip();
        }
        if (jp < js.length()) jp++;
        return l;
    }

    static String jStr() {
        jp++;  // opening "
        StringBuilder sb = new StringBuilder();
        while (jp < js.length()) {
            char c = js.charAt(jp++);
            if (c == '"') break;
            if (c == '\\' && jp < js.length()) {
                char e = js.charAt(jp++);
                sb.append(switch (e) {
                    case '"'  -> '"';
                    case '\\' -> '\\';
                    case 'n'  -> '\n';
                    case 'r'  -> '\r';
                    case 't'  -> '\t';
                    default   -> e;
                });
            } else sb.append(c);
        }
        return sb.toString();
    }

    static Number jNum() {
        int s = jp;
        while (jp < js.length() && "0123456789.-eE+".indexOf(js.charAt(jp)) >= 0) jp++;
        String n = js.substring(s, jp);
        try { return Long.parseLong(n); } catch (Exception e) { return Double.parseDouble(n); }
    }

    static void jSkip() {
        while (jp < js.length() && Character.isWhitespace(js.charAt(jp))) jp++;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    static String str(Map<String, Object> m, String k) {
        Object v = m.get(k); return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v instanceof Map<?,?> mp) ? (Map<String,Object>) mp : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v instanceof List<?> l) ? (List<Object>) l : new ArrayList<>();
    }

    static String error(Map<String, Object> m) {
        if (m.containsKey("error"))  return str(m, "error");
        if (m.containsKey("detail")) return str(m, "detail");
        return "Something went wrong.";
    }

    /** Build a simple flat JSON object from key/value pairs (strings only). */
    static String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length - 1; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":\"").append(esc(kv[i+1])).append('"');
        }
        return sb.append('}').toString();
    }

    /** Escape double-quotes and backslashes inside a JSON string value. */
    static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String pad(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** Read PIN — hidden in real terminals, visible fallback in IDEs. */
    static String readPin() {
        Console con = System.console();
        if (con != null) {
            char[] p = con.readPassword();
            String r = new String(p);
            Arrays.fill(p, ' ');
            return r;
        }
        return sc.nextLine().trim();
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