package com.restaurant.chat;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private static final String SYSTEM_PROMPT =
        "你是 Canteen 校園餐廳的智能助理，透過網頁聊天介面協助用戶。\n" +
        "語氣溫和真誠，回答簡短精煉，用繁體中文回覆（除非用戶用其他語言）。\n" +
        "多用換行與列點，重要字詞加**粗體**，切勿編造資訊。";

    private GeminiChatClient chatClient;

    @PostConstruct
    public void init() {
        this.chatClient = new GeminiChatClient(geminiApiKey);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatReq req) throws Exception {
        if (req.messages() == null || req.messages().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("reply", ""));

        List<GeminiChatClient.Message> messages = req.messages().stream()
            .map(m -> new GeminiChatClient.Message(m.role(), m.text()))
            .toList();

        try {
            String reply = chatClient.chat(SYSTEM_PROMPT, messages);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (GeminiChatClient.GeminiUnavailableException e) {
            return ResponseEntity.ok(Map.of("reply", e.getMessage()));
        }
    }

    record ChatReq(List<Message> messages) {
        record Message(String role, String text) {}
    }
}
