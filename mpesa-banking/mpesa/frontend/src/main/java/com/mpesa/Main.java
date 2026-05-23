package com.mpesa;

import java.util.Scanner;

/**
 * M-Pesa Banking System — Java Console Client
 *
 * Flow:
 *  1. Show splash screen
 *  2. Auth menu  → login / register / exit
 *  3. Main menu  → balance / send / deposit / statement / profile / logout
 */
public class Main {

    private static final Scanner SCANNER = new Scanner(System.in);

    public static void main(String[] args) {
        splash();

        // Auth loop — stay here until logged in or user quits
        while (!ApiClient.isLoggedIn()) {
            if (!authMenu()) {
                System.out.println("\nThank you for using M-Pesa. Goodbye! 👋");
                return;
            }
        }

        // Main banking menu
        bankingMenu();
    }

    // ──────────────────────────────────────────
    // SPLASH SCREEN
    // ──────────────────────────────────────────

    private static void splash() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║                                      ║");
        System.out.println("║    📱  M-PESA BANKING SYSTEM  📱    ║");
        System.out.println("║        Powered by Django REST        ║");
        System.out.println("║                                      ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
    }

    // ──────────────────────────────────────────
    // AUTH MENU
    // ──────────────────────────────────────────

    /**
     * @return true if user is now authenticated, false if they chose to exit
     */
    private static boolean authMenu() {
        System.out.println("  1. Login");
        System.out.println("  2. Create Account");
        System.out.println("  3. Exit");
        System.out.print("\nChoose: ");

        String choice = SCANNER.nextLine().trim();

        switch (choice) {
            case "1" -> { return doLogin(); }
            case "2" -> { doRegister(); return false; }  // redirect back to auth menu after register
            case "3" -> { return false; }
            default  -> System.out.println("❌ Invalid option.\n");
        }
        return false;
    }

    // ──────────────────────────────────────────
    // LOGIN FLOW
    // ──────────────────────────────────────────

    private static boolean doLogin() {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println("\n── LOGIN ──────────────────────────────");
            System.out.print("  Phone number : ");
            String phone = SCANNER.nextLine().trim();

            System.out.print("  PIN          : ");
            String pin = readPin();

            if (AuthService.login(phone, pin)) {
                return true;
            }

            int remaining = maxAttempts - attempt;
            if (remaining > 0) {
                System.out.println("  Attempts remaining: " + remaining);
            }
        }
        System.out.println("\n❌ Too many failed attempts.");
        return false;
    }

    // ──────────────────────────────────────────
    // REGISTER FLOW
    // ──────────────────────────────────────────

    private static void doRegister() {
        System.out.println("\n── CREATE ACCOUNT ─────────────────────");
        System.out.print("  Full name    : ");
        String name = SCANNER.nextLine().trim();

        System.out.print("  Phone number : ");
        String phone = SCANNER.nextLine().trim();

        System.out.print("  Choose PIN   : ");
        String pin = readPin();

        System.out.print("  Confirm PIN  : ");
        String confirm = readPin();

        if (!pin.equals(confirm)) {
            System.out.println("❌ PINs do not match. Please try again.");
            return;
        }

        AuthService.register(phone, name, pin);
    }

    // ──────────────────────────────────────────
    // MAIN BANKING MENU
    // ──────────────────────────────────────────

    private static void bankingMenu() {
        boolean running = true;

        while (running && ApiClient.isLoggedIn()) {
            System.out.println();
            System.out.println("══════════════════════════════════════");
            System.out.println("  Hello, " + AuthService.getLoggedInName());
            System.out.println("──────────────────────────────────────");
            System.out.println("  1. Check Balance");
            System.out.println("  2. Send Money");
            System.out.println("  3. Deposit Funds");
            System.out.println("  4. Mini Statement");
            System.out.println("  5. My Profile");
            System.out.println("  6. Logout");
            System.out.println("══════════════════════════════════════");
            System.out.print("  Select option: ");

            String choice = SCANNER.nextLine().trim();

            switch (choice) {
                case "1" -> BankingService.checkBalance();
                case "2" -> doSendMoney();
                case "3" -> doDeposit();
                case "4" -> BankingService.viewStatement();
                case "5" -> BankingService.viewProfile();
                case "6" -> { AuthService.logout(); running = false; }
                default  -> System.out.println("❌ Invalid option. Choose 1–6.");
            }
        }
    }

    // ──────────────────────────────────────────
    // SEND MONEY FLOW
    // ──────────────────────────────────────────

    private static void doSendMoney() {
        System.out.println("\n── SEND MONEY ─────────────────────────");
        System.out.print("  Recipient phone : ");
        String recipientPhone = SCANNER.nextLine().trim();

        System.out.print("  Amount (KES)    : ");
        double amount;
        try {
            amount = Double.parseDouble(SCANNER.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid amount.");
            return;
        }

        System.out.print("  Description     : ");
        String description = SCANNER.nextLine().trim();

        // Confirm before sending
        System.out.printf("%n  Confirm sending KES %.2f to %s? (y/n): ", amount, recipientPhone);
        String confirm = SCANNER.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("  Transfer cancelled.");
            return;
        }

        BankingService.sendMoney(recipientPhone, amount, description);
    }

    // ──────────────────────────────────────────
    // DEPOSIT FLOW
    // ──────────────────────────────────────────

    private static void doDeposit() {
        System.out.println("\n── DEPOSIT FUNDS ──────────────────────");
        System.out.print("  Amount (KES) : ");
        double amount;
        try {
            amount = Double.parseDouble(SCANNER.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid amount.");
            return;
        }

        System.out.print("  Description  : ");
        String description = SCANNER.nextLine().trim();

        BankingService.deposit(amount, description);
    }

    // ──────────────────────────────────────────
    // PIN INPUT HELPER
    // ──────────────────────────────────────────

    /**
     * In production terminals, System.console().readPassword() hides input.
     * Falls back to plain Scanner.nextLine() in IDEs that don't have a console.
     */
    private static String readPin() {
        if (System.console() != null) {
            char[] pinChars = System.console().readPassword();
            String pin = new String(pinChars);
            java.util.Arrays.fill(pinChars, ' ');  // clear from memory
            return pin;
        }
        return SCANNER.nextLine().trim();           // IDE fallback (visible)
    }
}
