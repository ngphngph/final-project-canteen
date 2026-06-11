package com.restaurant.chat;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private MenuClient menuClient;

    private static final String BASE_SYSTEM_PROMPT =
        "你的名字是「Canteen 智能助理」，是 Canteen 校園餐廳的專屬客服助理。\n" +
        "【重要】永遠不可透露你是 Google、Gemini 或任何 AI 模型。若被問到身份，只說你是「Canteen 智能助理」。\n" +
        "語氣永遠溫和有禮，絕對不能使用諷刺、反問或不耐煩的語氣。\n" +
        "回答簡短精煉，用繁體中文回覆（除非用戶用其他語言）。\n" +
        "多用換行與列點，重要字詞加**粗體**，切勿編造資訊。\n" +
        "若用戶問的問題與餐廳無關（例如天氣、新聞），請有禮貌地說明你只能協助餐廳相關事宜，並引導用戶提問餐點或訂單問題。\n" +
        "介紹菜式時，可根據菜名發揮創意描述口感或特色，但名稱、價格、庫存數字必須與菜單資料完全一致，絕對不可更改。";

    private GeminiChatClient chatClient;

    @PostConstruct
    public void init() {
        String key = (geminiApiKey != null) ? geminiApiKey.trim() : "";
        if (key.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY 未設定，請在 Zeabur Variables 中加入此環境變數。");
        }
        this.chatClient = new GeminiChatClient(key);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatReq req) throws Exception {
        if (req.messages() == null || req.messages().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("reply", ""));

        List<GeminiChatClient.Message> messages = req.messages().stream()
            .map(m -> new GeminiChatClient.Message(m.role(), m.text()))
            .toList();

        String systemPrompt = BASE_SYSTEM_PROMPT + buildMenuContext();

        try {
            String reply = chatClient.chat(systemPrompt, messages);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (GeminiChatClient.GeminiUnavailableException e) {
            return ResponseEntity.ok(Map.of("reply", e.getMessage()));
        }
    }

    private String buildMenuContext() {
        StringBuilder sb = new StringBuilder();
        try {
            List<Map<String, Object>> dishes = menuClient.getDishesToday();
            if (dishes != null && !dishes.isEmpty()) {
                sb.append("\n\n【今日主食菜單】");
                for (Map<String, Object> d : dishes) {
                    String name   = String.valueOf(d.getOrDefault("name", ""));
                    String price  = String.valueOf(d.getOrDefault("price", ""));
                    Object bal    = d.get("balance");
                    String status = String.valueOf(d.getOrDefault("status", ""));
                    String stock  = "SOLD_OUT".equals(status) ? "售罄"
                                  : (bal != null ? "餘 " + bal + " 份" : "");
                    sb.append("\n- ").append(name).append(" $").append(price);
                    if (!stock.isEmpty()) sb.append("（").append(stock).append("）");
                }
            }

            List<Map<String, Object>> drinks = menuClient.getDrinkesToday();
            if (drinks != null && !drinks.isEmpty()) {
                sb.append("\n\n【今日飲品菜單】");
                for (Map<String, Object> d : drinks) {
                    String name   = String.valueOf(d.getOrDefault("name", ""));
                    String price  = String.valueOf(d.getOrDefault("price", ""));
                    Object bal    = d.get("balance");
                    String status = String.valueOf(d.getOrDefault("status", ""));
                    String stock  = "SOLD_OUT".equals(status) ? "售罄"
                                  : (bal != null ? "餘 " + bal + " 份" : "");
                    sb.append("\n- ").append(name).append(" $").append(price);
                    if (!stock.isEmpty()) sb.append("（").append(stock).append("）");
                }
            }

            Map<String, Object> window = menuClient.getOrderWindow();
            if (window != null) {
                Boolean enforced = (Boolean) window.get("enforced");
                if (Boolean.TRUE.equals(enforced)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> slots = (List<Map<String, Object>>) window.get("slots");
                    if (slots != null && !slots.isEmpty()) {
                        sb.append("\n\n【訂餐時間】");
                        for (Map<String, Object> slot : slots) {
                            sb.append("\n- ").append(slot.get("start"))
                              .append(" – ").append(slot.get("end"));
                        }
                        sb.append("（香港時間）");
                    }
                } else {
                    sb.append("\n\n【訂餐時間】全日開放");
                }
            }
        } catch (Exception e) {
            System.out.println("[ChatController] 菜單資料載入失敗，略過：" + e.getMessage());
        }
        return sb.toString();
    }

    record ChatReq(List<Message> messages) {
        record Message(String role, String text) {}
    }
}
