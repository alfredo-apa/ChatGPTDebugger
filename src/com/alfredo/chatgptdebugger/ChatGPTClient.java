/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.alfredo.chatgptdebugger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ChatGPTClient {

    // You can change this model name if you want another one later.
    private static final String MODEL = "gpt-4o-mini";

    public static String askForDebugHelp(String userText) throws IOException {
        // Read API key from environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY environment variable not set.\n" +
                    "Please configure your OpenAI API key before using ChatGPTDebugger."
            );
        }

        // System prompt specialized for debugging
        String systemPrompt =
                "You are a senior Java/NetBeans developer. "
              + "The user will send compiler errors, stack traces, and code snippets "
              + "from Apache NetBeans projects. Explain clearly what is wrong and "
              + "propose concrete fixes with line examples.";

        // Build JSON body for the Chat Completions API
        // https://api.openai.com/v1/chat/completions :contentReference[oaicite:0]{index=0}
        
        String jsonBody =
                "{\n" +
                "  \"model\": " + escapeJson(MODEL) + ",\n" +
                "  \"messages\": [\n" +
                "    { \"role\": \"system\", \"content\": " + escapeJson(systemPrompt) + " },\n" +
                "    { \"role\": \"user\", \"content\": " + escapeJson(userText) + " }\n" +
                "  ],\n" +
                "  \"temperature\": 0.2\n" +
                "}"
        .formatted(
                escapeJson(MODEL),
                escapeJson(systemPrompt),
                escapeJson(userText)
        );

        URL url = new URL("https://api.openai.com/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        // Send request body
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        String response = readAll(is);

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + ":\n" + response);
        }

        // Very naive extraction of the first "content" field.
        // For a real project use a JSON library, but this keeps the plugin dependency-free.
        String marker = "\"content\":";
        int idx = response.indexOf(marker);
        if (idx == -1) {
            return "No 'content' field found in response:\n" + response;
        }

        int firstQuote = response.indexOf('"', idx + marker.length());
        int lastQuote = response.indexOf('"', firstQuote + 1);
        if (firstQuote == -1 || lastQuote == -1) {
            return "Could not parse assistant message from response:\n" + response;
        }

        String raw = response.substring(firstQuote + 1, lastQuote);
        return raw
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String escapeJson(String text) {
        if (text == null) return "null";
        String escaped = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}
