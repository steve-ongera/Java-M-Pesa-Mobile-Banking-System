package com.mpesa;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Low-level HTTP client that talks to the Django REST API.
 * Automatically attaches the Bearer token when logged in.
 */
public class ApiClient {

    private static final String BASE_URL = "http://127.0.0.1:8000/api";
    private static final MediaType JSON   = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,    TimeUnit.SECONDS)
            .writeTimeout(10,   TimeUnit.SECONDS)
            .build();

    private static final Gson GSON = new Gson();

    /** Stored after a successful login. */
    private static String authToken = null;

    public static void  setToken(String token) { authToken = token; }
    public static String getToken()            { return authToken;  }
    public static boolean isLoggedIn()         { return authToken != null; }
    public static void clearToken()            { authToken = null; }

    // ──────────────────────────────────────────
    // PUBLIC HTTP METHODS
    // ──────────────────────────────────────────

    /**
     * POST request — used for login, register, send money, deposit.
     * @param endpoint  e.g. "/auth/login/"
     * @param body      Gson JsonObject payload
     * @return parsed JSON response
     */
    public static JsonObject post(String endpoint, JsonObject body) throws IOException {
        RequestBody reqBody = RequestBody.create(body.toString(), JSON);
        Request request = buildRequest(endpoint)
                .post(reqBody)
                .build();
        return execute(request);
    }

    /**
     * GET request — used for balance, statement, profile.
     * @param endpoint  e.g. "/account/balance/"
     * @return parsed JSON response
     */
    public static JsonObject get(String endpoint) throws IOException {
        Request request = buildRequest(endpoint)
                .get()
                .build();
        return execute(request);
    }

    // ──────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────

    private static Request.Builder buildRequest(String endpoint) {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept",       "application/json");

        if (authToken != null) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }
        return builder;
    }

    private static JsonObject execute(Request request) throws IOException {
        try (Response response = HTTP.newCall(request).execute()) {
            String rawJson = response.body() != null ? response.body().string() : "{}";

            if (rawJson.isEmpty()) rawJson = "{}";

            JsonObject result = GSON.fromJson(rawJson, JsonObject.class);

            // Attach HTTP status so callers can check it
            result.addProperty("_status_code", response.code());
            return result;
        }
    }
}
