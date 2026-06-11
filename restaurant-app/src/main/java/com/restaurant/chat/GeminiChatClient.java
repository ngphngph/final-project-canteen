package com.restaurant.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Gemini 原生 API 客戶端。
 *
 * 端點：POST /v1beta/models/gemini-1.5-flash:generateContent?key={key}
 * 認證：?key= query string（原生門牌專用）
 * 格式：{ "system_instruction": {...}, "contents": [...] }
 *
 * 503 退避策略：等 1s → 等 2s → 拋出 GeminiUnavailableException
 */
public class GeminiChatClient {

    private static final String ENDPOINT =
        "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent";

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GeminiChatClient(String apiKey) {
        this.apiKey  = apiKey;
        this.http    = HttpClient.newHttpClient();
        this.mapper  = new ObjectMapper();
    }

    public String chat(String systemPrompt, List<Message> messages) throws Exception {

        HttpResponse<String> res = callNative(systemPrompt, messages);

        if (res.statusCode() == 503) {
            Thread.sleep(1_000);
            res = callNative(systemPrompt, messages);
        }
        if (res.statusCode() == 503) {
            Thread.sleep(2_000);
            res = callNative(systemPrompt, messages);
        }
        if (res.statusCode() == 503) {
            throw new GeminiUnavailableException("Gemini 服務目前壅塞，請稍後再試。");
        }

        return parseReply(res);
    }

    private HttpResponse<String> callNative(String systemPrompt,
                                            List<Message> messages) throws Exception {
        ObjectNode body = mapper.createObjectNode();

        // 系統提示
        ObjectNode sysInstruction = mapper.createObjectNode();
        ArrayNode sysParts = mapper.createArrayNode();
        sysParts.add(mapper.createObjectNode().put("text", systemPrompt));
        sysInstruction.set("parts", sysParts);
        body.set("system_instruction", sysInstruction);

        // 對話內容（role 只接受 "user" / "model"）
        ArrayNode contents = mapper.createArrayNode();
        for (Message m : messages) {
            ObjectNode content = mapper.createObjectNode();
            content.put("role", "assistant".equals(m.role()) ? "model" : m.role());
            ArrayNode parts = mapper.createArrayNode();
            parts.add(mapper.createObjectNode().put("text", m.text()));
            content.set("parts", parts);
            contents.add(content);
        }
        body.set("contents", contents);

        ObjectNode genConfig = mapper.createObjectNode();
        genConfig.put("maxOutputTokens", 400);
        body.set("generationConfig", genConfig);

        String url = ENDPOINT + "?key=" + apiKey;
        System.out.println("Calling URL: " + url.replace(apiKey, "REDACTED")); // 確認路徑，隱藏 key

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String parseReply(HttpResponse<String> res) throws Exception {
        if (res.statusCode() != 200) {
            JsonNode err = mapper.readTree(res.body());
            String msg = err.path("error").path("message").asText(res.body());
            return "[Gemini錯誤] " + msg;
        }
        return mapper.readTree(res.body())
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText("服務暫時無法回應，請稍後再試。");
    }

    static class GeminiUnavailableException extends RuntimeException {
        GeminiUnavailableException(String msg) { super(msg); }
    }

    record Message(String role, String text) {}
}
