package com.restaurant.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=";

    private static final String SYSTEM_PROMPT =
        "你是 Canteen 校園餐廳的智能助理，透過網頁聊天介面協助用戶。\n" +
        "語氣溫和真誠，回答簡短精煉，用繁體中文回覆（除非用戶用其他語言）。\n" +
        "多用換行與列點，重要字詞加**粗體**，切勿編造資訊。";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @PostMapping
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatReq req) throws Exception {
        if (req.messages() == null || req.messages().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("reply", ""));

        // Build Gemini request body
        ObjectNode body = mapper.createObjectNode();
        ObjectNode sysInstruction = mapper.createObjectNode();
        ArrayNode sysParts = mapper.createArrayNode();
        sysParts.add(mapper.createObjectNode().put("text", SYSTEM_PROMPT));
        sysInstruction.set("parts", sysParts);
        body.set("system_instruction", sysInstruction);

        ArrayNode contents = mapper.createArrayNode();
        for (ChatReq.Message msg : req.messages()) {
            ObjectNode content = mapper.createObjectNode();
            content.put("role", msg.role());
            ArrayNode parts = mapper.createArrayNode();
            parts.add(mapper.createObjectNode().put("text", msg.text()));
            content.set("parts", parts);
            contents.add(content);
        }
        body.set("contents", contents);
        ObjectNode genConfig = mapper.createObjectNode();
        genConfig.put("maxOutputTokens", 400);
        body.set("generationConfig", genConfig);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GEMINI_URL + geminiApiKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode result = mapper.readTree(response.body());

        if (response.statusCode() != 200 || !result.has("candidates")) {
            String errMsg = result.path("error").path("message").asText(response.body());
            return ResponseEntity.ok(Map.of("reply", "[Gemini錯誤] " + errMsg));
        }

        String reply = result.path("candidates").path(0)
            .path("content").path("parts").path(0)
            .path("text").asText("服務暫時無法回應，請稍後再試。");

        return ResponseEntity.ok(Map.of("reply", reply));
    }

    record ChatReq(List<Message> messages) {
        record Message(String role, String text) {}
    }
}
