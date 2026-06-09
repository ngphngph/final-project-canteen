# N8N 整合備案 — WhatsApp 取餐通知

> 狀態：**計劃中，尚未實作**
> 討論日期：2026-06-09
> 功能描述：廚房按「叫號」後，系統自動透過 N8N 發送 WhatsApp 通知用戶取餐

---

## 一、功能目標

廚房完成備餐，按下叫號按鈕時：

1. 現有流程（已實作）：WebSocket 廣播到廚房螢幕
2. **新增流程**：同步觸發 N8N Webhook → N8N 發送 WhatsApp 給用戶手機

---

## 二、整合架構

```
廚房按「叫號」按鈕
    ↓
POST /api/pickups/{pickupId}/call
    ↓
MealPickupService.callPickup()
    ├─ webSocketHandler.broadcast(...)   ← 現有，保留
    └─ n8nNotificationService.notify()  ← 新增，@Async fire-and-forget
                ↓
        HTTP POST → N8N Webhook URL
                ↓
          N8N Workflow
          [Webhook Trigger]
                ↓
          [WhatsApp Node]
                ↓
        用戶手機收到 WhatsApp 訊息
```

### Webhook Payload（Spring Boot → N8N）

```json
{
  "pickupId": 123,
  "itemId": 456,
  "pickupCode": "0289-003",
  "phone": "0912345678",
  "message": "您的餐點已備妥，請憑取餐碼 0289-003 取餐"
}
```

---

## 三、現有程式碼切入點

### 觸發位置

**檔案：** `restaurant-app/src/main/java/com/restaurant/pickup/service/MealPickupService.java`

```java
// line 52 — 現有方法，N8N 呼叫加在這裡
public PickupResponse callPickup(Long pickupId) {
    MealPickup pickup = repository.findById(pickupId)
            .orElseThrow(() -> new IllegalArgumentException("找不到 pickupId: " + pickupId));
    webSocketHandler.broadcast(PickupCallMessage.of(pickupId, pickup.getMethod()));
    // ← 在這裡加：n8nNotificationService.notifyPickupReady(pickup);
    return toResponse(pickup, "叫號廣播已發送");
}
```

**API 端點：**

```
POST /api/pickups/{pickupId}/call
```

**對應 Controller：** `MealPickupController.java` line 56

---

## 四、最大設計問題：電話號碼從哪裡來？

`MealPickup` entity 目前只有：

| 欄位 | 說明 |
|------|------|
| `pickupId` | 主鍵 |
| `itemId` | 對應 OrderItemEntity |
| `method` | 取餐碼（格式：`userId%10000 - orderId%1000`） |
| `expectedTime` | 預計取餐時間 |
| `actualTime` | 實際取餐時間 |
| `adminId` | 核銷的管理員 |
| `adminNotified` | 是否已通知 |

**沒有 `phone` 欄位。** 要通知用戶，需要透過以下路徑取得：

```
pickupId → itemId → OrderItemEntity.orderId → OrderEntity.userId → login module → phone
```

### 三個方案比較

| 方案 | 做法 | 優點 | 缺點 |
|------|------|------|------|
| **A（推薦）** | `createPickup` 時把 `phone` 存進 `MealPickup` entity | 最簡單，`callPickup` 直接讀取 | 需新增 DB 欄位 `phone VARCHAR(20)` |
| **B** | `callPickup` 時 Feign 呼叫 order module → login module | 不改 entity | 關鍵路徑多 2 次 HTTP 呼叫，且需新增兩個 FeignClient |
| **C** | N8N Webhook 只收 `itemId`，N8N 自己反查 Spring Boot API | Spring Boot 最乾淨 | N8N 需帶 auth header 呼叫 API，N8N 設定複雜 |

### 方案 A 的實作變動

1. `MealPickup` entity 加欄位：
```java
@Column(name = "phone", length = 20)
private String phone;
```

2. `PickupCreateRequest` DTO 加欄位：
```java
private String phone;
```

3. `MealPickupService.createPickup()` 存入 phone：
```java
pickup.setPhone(req.getPhone());
```

4. 前端 `createPickup` 時從 `localStorage` 的 `canteen_session.phone` 帶入

---

## 五、N8N 部署方案

### 記憶體預算（2 GB EC2 現況）

```
PostgreSQL 18        400 MB
restaurant-app       700 MB
Nginx                ~50 MB
OS / Buffer          ~850 MB
─────────────────────────────
合計                 ~2 GB  ← 已接近上限
```

| 方案 | RAM 開銷 | 建議 |
|------|----------|------|
| **Self-hosted（同台 EC2）** | +300 MB → 推至 ~2.3 GB，有 OOM 風險 | ❌ 不建議 |
| **N8N Cloud 免費版** | 0（在雲端執行） | ✅ 推薦，5 個 workflow 免費，足夠本案 |

**N8N Cloud 免費版限制：**
- 5 個 active workflow
- 每月 2,500 次執行
- 單次執行時間 ≤ 60 秒

對校園 POS（每日訂單量數百筆）完全足夠。

---

## 六、WhatsApp 發送方案

N8N 支援以下方式發送 WhatsApp：

| 方案 | 費用 | 設定難度 | 備註 |
|------|------|----------|------|
| **Twilio WhatsApp** | ~$0.005 USD / 則 | 低，N8N 有內建 Twilio node | 需 Twilio 帳號 + WhatsApp sender 申請 |
| **Meta WhatsApp Business Cloud API** | 每月 1,000 則免費 | 中，需申請 Meta Business 帳號 | 適合正式營運；審核約 1–2 天 |
| **360dialog** | 按量付費 | 中 | 第三方代理 Meta API |

**MVP 階段推薦：Twilio**（最快上線，N8N 一鍵設定）

---

## 七、完整實作步驟（待執行）

### Step 1 — 修改 MealPickup entity（方案 A）

```
MealPickup.java        → 加 phone 欄位
PickupCreateRequest.java → 加 phone 欄位
MealPickupService.java  → createPickup() 存入 phone
```

### Step 2 — 加 application.yml 設定

```yaml
n8n:
  webhook-url: https://your-n8n-cloud.app.n8n.cloud/webhook/pickup-notify
  enabled: true
```

### Step 3 — 建立 N8nNotificationService

位置：`com/restaurant/pickup/service/N8nNotificationService.java`

```java
@Service
@RequiredArgsConstructor
public class N8nNotificationService {

    @Value("${n8n.webhook-url}")
    private String webhookUrl;

    @Value("${n8n.enabled:true}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    @Async
    public void notifyPickupReady(Long pickupId, String phone, String pickupCode) {
        if (!enabled || phone == null) return;
        try {
            Map<String, Object> payload = Map.of(
                "pickupId", pickupId,
                "phone", phone,
                "pickupCode", pickupCode,
                "message", "您的餐點已備妥，請憑取餐碼 " + pickupCode + " 取餐"
            );
            restTemplate.postForObject(webhookUrl, payload, String.class);
        } catch (Exception e) {
            // fire-and-forget：N8N 失敗不影響主流程
        }
    }
}
```

### Step 4 — 修改 callPickup()

```java
public PickupResponse callPickup(Long pickupId) {
    MealPickup pickup = repository.findById(pickupId)
            .orElseThrow(() -> new IllegalArgumentException("找不到 pickupId: " + pickupId));
    webSocketHandler.broadcast(PickupCallMessage.of(pickupId, pickup.getMethod()));
    n8nNotificationService.notifyPickupReady(pickupId, pickup.getPhone(), pickup.getMethod());
    return toResponse(pickup, "叫號廣播已發送");
}
```

### Step 5 — N8N Cloud Workflow 設定

```
[Webhook Trigger]
  Method: POST
  Path: /pickup-notify
       ↓
[Set Node]（組合 WhatsApp 訊息內容）
  message: "{{$json.message}}"
  to: "{{$json.phone}}"
       ↓
[Twilio Node 或 WhatsApp Business Node]
  Send message to {{ to }}
```

### Step 6 — 前端 createPickup 帶入 phone

```javascript
// 廚房 / 前台建立取餐記錄時
const session = JSON.parse(localStorage.getItem('canteen_session'));
await fetch('/api/pickups/create', {
    method: 'POST',
    body: JSON.stringify({
        itemId: itemId,
        expectedTime: expectedTime,
        method: pickupCode,
        phone: session?.phone ?? null   // ← 新增
    })
});
```

---

## 八、決策清單（實作前需確認）

- [ ] **電話來源方案**：採用方案 A（改 entity）/ B（Feign）/ C（N8N 反查）？
- [ ] **N8N 部署**：N8N Cloud 免費版 或 自建？
- [ ] **WhatsApp Provider**：Twilio 或 Meta Business API？
- [ ] **N8N Webhook URL**：設定後填入 `application.yml`
- [ ] **Twilio/Meta API Key**：申請後設定到 N8N Cloud

---

## 九、相關檔案路徑

| 檔案 | 路徑 |
|------|------|
| Pickup Service（觸發點）| `restaurant-app/src/main/java/com/restaurant/pickup/service/MealPickupService.java` |
| Pickup Controller | `restaurant-app/src/main/java/com/restaurant/pickup/controller/MealPickupController.java` |
| Pickup Entity | `restaurant-app/src/main/java/com/restaurant/pickup/entity/MealPickup.java` |
| Pickup Create DTO | `restaurant-app/src/main/java/com/restaurant/pickup/dto/PickupCreateRequest.java` |
| App Config | `restaurant-app/src/main/resources/application.yml` |
| Architecture 說明 | `restaurant-app/src/main/resources/static/architecture.html` |

---

## 十、Demo Cart 分析備案（同次討論）

同日評估了 `demo-restaurant-main` 的 Server-side Cart，決定**不移植**，原因：

1. 價格硬碼（`$40`），需 Feign 取真實價格
2. Deposit 邏輯錯誤：`finalTotal = baseTotal + depositAmt`（應為先付部分，非加項）
3. 認證用 `X-User-Id` header，與現行 Spring Security 不符
4. 校園 POS 單一餐期，localStorage Cart 已足夠，不需 server-side Cart

決策已記錄於 `architecture.html` 第 3.4 節。

---

*備案建立：2026-06-09 | 作者：Claude Code*
