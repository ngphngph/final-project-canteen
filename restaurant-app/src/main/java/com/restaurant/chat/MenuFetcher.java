package com.restaurant.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class MenuFetcher {

    private static final String BASE = "http://127.0.0.1:8080";
    private final HttpClient http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String buildMenuPrompt() {
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode dishes = get("/api/menu/dishes/today");
            if (dishes.isArray() && !dishes.isEmpty()) {
                sb.append("\n\n【今日主食菜單】");
                for (JsonNode d : dishes) {
                    appendItem(sb, d);
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
                    appendItem(sb, d);
                }
            }
        } catch (Exception e) {
            System.out.println("[MenuFetcher] 飲品載入失敗：" + e.getMessage());
        }

        try {
            JsonNode window = get("/api/menu/order-window");
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

        return sb.toString();
    }

    private void appendItem(StringBuilder sb, JsonNode d) {
        String name   = d.path("name").asText("");
        String price  = d.path("price").asText("");
        int    bal    = d.path("balance").asInt(-1);
        String status = d.path("status").asText("");

        if (name.isBlank()) return;
        sb.append("\n- ").append(name).append(" $").append(price);
        if ("SOLD_OUT".equals(status)) {
            sb.append("（售罄）");
        } else if (bal >= 0) {
            sb.append("（餘 ").append(bal).append(" 份）");
        }
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
