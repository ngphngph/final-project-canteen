package com.restaurant.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class MenuFetcher {

    private static final String BASE = "http://127.0.0.1:8080";
    private final HttpClient http     = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String buildMenuPrompt() {
        StringBuilder sb = new StringBuilder();
        List<String> dishNames  = new ArrayList<>();
        List<String> drinkNames = new ArrayList<>();

        try {
            JsonNode dishes = get("/api/menu/dishes/today");
            if (dishes.isArray() && !dishes.isEmpty()) {
                sb.append("\n\n【今日主食菜單】");
                for (JsonNode d : dishes) {
                    String line = formatItem(d);
                    if (line != null) {
                        sb.append("\n- ").append(line);
                        dishNames.add(d.path("name").asText());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[MenuFetcher] 主食載入失敗：" + e.getMessage());
        }

        try {
            JsonNode drinks = get("/api/menu/drinks/today");
            if (drinks.isArray() && !drinks.isEmpty()) {
                sb.append("\n\n【今日飲品菜單】");
                for (JsonNode d : drinks) {
                    String line = formatItem(d);
                    if (line != null) {
                        sb.append("\n- ").append(line);
                        drinkNames.add(d.path("name").asText());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[MenuFetcher] 飲品載入失敗：" + e.getMessage());
        }

        try {
            JsonNode window  = get("/api/menu/order-window");
            boolean enforced = window.path("enforced").asBoolean(true);
            JsonNode slots   = window.path("slots");
            if (enforced && slots.isArray() && !slots.isEmpty()) {
                sb.append("\n\n【訂餐時間】");
                for (JsonNode slot : slots) {
                    sb.append("\n- ").append(slot.path("start").asText())
                      .append(" – ").append(slot.path("end").asText());
                }
                sb.append("（香港時間）");
            } else if (!enforced) {
                sb.append("\n\n【訂餐時間】全日開放");
            }
        } catch (Exception e) {
            System.out.println("[MenuFetcher] 訂餐時間載入失敗：" + e.getMessage());
        }

        // 封頂聲明：明確告訴 AI 今日菜單的完整數量，杜絕自由創作
        if (!dishNames.isEmpty() || !drinkNames.isEmpty()) {
            sb.append("\n\n⚠️ 今日餐牌完整清單如上，共主食 ").append(dishNames.size())
              .append(" 款、飲品 ").append(drinkNames.size())
              .append(" 款，不多不少。");
            sb.append("\n任何不在以上清單的菜式或飲品，今日一律沒有提供。");
            sb.append("\n介紹菜式時只可根據菜名創作描述，菜名、價格、庫存數字必須與清單完全一致，不可自行增減或更改。");
        } else {
            sb.append("\n\n⚠️ 今日餐牌尚未更新，請告知用戶稍後再查詢或聯絡餐廳職員。");
        }

        return sb.toString();
    }

    private String formatItem(JsonNode d) {
        String name   = d.path("name").asText("");
        String price  = d.path("price").asText("");
        int    bal    = d.path("balance").asInt(-1);
        String status = d.path("status").asText("");

        if (name.isBlank()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" $").append(price);
        if ("SOLD_OUT".equals(status)) {
            sb.append("（售罄）");
        } else if (bal >= 0) {
            sb.append("（餘 ").append(bal).append(" 份）");
        }
        return sb.toString();
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200)
            throw new RuntimeException("HTTP " + res.statusCode() + " from " + path);
        return mapper.readTree(res.body());
    }
}
