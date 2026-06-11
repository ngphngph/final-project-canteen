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
 * 核心功能：指數退避重試 + 主備模型降級。
 *
 * 重試策略：
 *   第 1 次 503 → 等待 1 秒，重試主要模型（gemini-2.5-pro）
 *   第 2 次 503 → 等待 2 秒，降級至備用模型（gemini-2.5-flash）
 *   備用模型也 503 → 拋出 GeminiUnavailableException
 */
public class GeminiChatClient {

    // 主要模型：能力最強，優先使用
    private static final String PRIMARY_MODEL = "gemini-2.5-pro";

    // 備用模型：主要模型連續失敗後降級使用
    private static final String FALLBACK_MODEL = "gemini-2.5-flash";

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

        // ── 第 1 次嘗試：呼叫主要模型 ──────────────────────────
        HttpResponse<String> res = callModel(PRIMARY_MODEL, systemPrompt, messages);

        if (res.statusCode() == 503) {
            // 第 1 次 503：等待 1 秒後重試主要模型
            Thread.sleep(1_000);
            res = callModel(PRIMARY_MODEL, systemPrompt, messages);
        }

        if (res.statusCode() == 503) {
            // 第 2 次 503：等待 2 秒後降級至備用模型
            Thread.sleep(2_000);
            res = callModel(FALLBACK_MODEL, systemPrompt, messages);
        }

        if (res.statusCode() == 503) {
            // 備用模型也失敗：放棄並通知呼叫方
            throw new GeminiUnavailableException("Gemini 服務目前壅塞，請稍後再試。");
        }

        return parseReply(res);
    }

    // ── 私有方法 ─────────────────────────────────────────────────

    /** 對指定模型發出一次 HTTP 請求 */
    private HttpResponse<String> callModel(String model,
                                           String systemPrompt,
                                           List<Message> messages) throws Exception {
        // 使用 Text Block 建構 JSON 請求體（model 欄位嵌入）
        String requestBody = """
                {
                    "model": "%s",
                    "messages": %s,
                    "max_tokens": 400
                }
                """.formatted(model, buildMessagesJson(systemPrompt, messages));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
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
