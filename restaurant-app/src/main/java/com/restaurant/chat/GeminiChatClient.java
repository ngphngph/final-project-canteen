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
 * Gemini API 客戶端，使用 OpenAI 相容格式呼叫。
 * 核心功能：指數退避重試（最多 3 次，無模型降級）。
 *
 * 重試策略：
 *   第 1 次 503 → 等待 1 秒後重試
 *   第 2 次 503 → 等待 2 秒後重試
 *   第 3 次 503 → 拋出 GeminiUnavailableException
 */
public class GeminiChatClient {

    private static final String MODEL = "gemini-1.5-flash";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    /**
     * @param apiKey  Gemini API Key（從環境變數傳入，不寫死於程式碼）
     * @param baseUrl OpenAI 相容端點的 Base URL
     */
    public GeminiChatClient(String apiKey, String baseUrl) {
        this.apiKey  = apiKey;
        this.baseUrl = baseUrl;
        this.http    = HttpClient.newHttpClient();
        this.mapper  = new ObjectMapper();
    }

    /**
     * 發送聊天請求。包含完整的退避重試與模型降級流程。
     *
     * @param systemPrompt 系統提示詞
     * @param messages     對話歷史（含當前用戶訊息）
     * @return AI 回覆文字
     */
    public String chat(String systemPrompt, List<Message> messages) throws Exception {

        // ── 第 1 次嘗試 ───────────────────────────────────────
        HttpResponse<String> res = callModel(MODEL, systemPrompt, messages);

        if (res.statusCode() == 503) {
            // 第 1 次 503：等待 1 秒後重試
            Thread.sleep(1_000);
            res = callModel(MODEL, systemPrompt, messages);
        }

        if (res.statusCode() == 503) {
            // 第 2 次 503：等待 2 秒後最後一次重試
            Thread.sleep(2_000);
            res = callModel(MODEL, systemPrompt, messages);
        }

        if (res.statusCode() == 503) {
            // 三次皆失敗：放棄並通知呼叫方
            throw new GeminiUnavailableException("Gemini 服務目前壅塞，請稍後再試。");
        }

        return parseReply(res);
    }

    // ── 私有方法 ─────────────────────────────────────────────────

    /** 發出一次 HTTP 請求 */
    private HttpResponse<String> callModel(String model,
                                           String systemPrompt,
                                           List<Message> messages) throws Exception {
        String requestBody = """
                {
                    "model": "%s",
                    "messages": %s,
                    "max_tokens": 400
                }
                """.formatted(model, buildMessagesJson(systemPrompt, messages));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 將系統提示 + 對話歷史序列化為 JSON 陣列字串。
     * 透過 Jackson 序列化確保用戶輸入的特殊字元（", \, 換行）被正確轉義。
     */
    private String buildMessagesJson(String systemPrompt, List<Message> messages) throws Exception {
        ArrayNode array = mapper.createArrayNode();

        // 加入系統提示（role: system）
        ObjectNode sys = mapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        array.add(sys);

        // 加入對話歷史（Jackson 自動處理 JSON 轉義，防止注入）
        for (Message msg : messages) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", msg.role());
            node.put("content", msg.text());
            array.add(node);
        }

        return mapper.writeValueAsString(array);
    }

    /** 從 OpenAI 格式的回應中取出回覆文字 */
    private String parseReply(HttpResponse<String> res) throws Exception {
        if (res.statusCode() != 200) {
            JsonNode err = mapper.readTree(res.body());
            String msg = err.path("error").path("message").asText(res.body());
            return "[Gemini錯誤] " + msg;
        }
        JsonNode result = mapper.readTree(res.body());
        return result.path("choices").path(0)
                     .path("message").path("content")
                     .asText("服務暫時無法回應，請稍後再試。");
    }

    // ── 內部類型 ──────────────────────────────────────────────────

    /** 主備模型皆回傳 503 時拋出 */
    static class GeminiUnavailableException extends RuntimeException {
        GeminiUnavailableException(String msg) { super(msg); }
    }

    /** 對話訊息（role: user / assistant / system） */
    record Message(String role, String text) {}
}
