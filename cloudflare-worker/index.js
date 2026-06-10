export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET") {
      const mode      = url.searchParams.get("hub.mode");
      const token     = url.searchParams.get("hub.verify_token");
      const challenge = url.searchParams.get("hub.challenge");
      if (mode === "subscribe" && token === env.VERIFY_TOKEN)
        return new Response(challenge, { status: 200 });
      return new Response("Forbidden", { status: 403 });
    }

    if (request.method === "POST") {
      const body = await request.json();
      const msg  = body?.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
      if (!msg || msg.type !== "text") return new Response("OK");

      const from = msg.from;
      const text = msg.text.body.trim().toUpperCase();

      const apiRes = await fetch(
        `${env.JAVA_API_BASE}/api/pickups/status?code=${encodeURIComponent(text)}`
      );
      const data = apiRes.ok ? await apiRes.json() : null;

      let reply;
      if (!data || data.status === "NOT_FOUND") {
        reply = `找不到取餐碼 (${text})，請確認後再試。`;
      } else if (data.status === "READY") {
        reply = `取餐碼 (${data.code}) 已完成，${data.expectedTime ?? ""} 可取餐。`;
      } else {
        reply = `取餐碼 (${data.code}) 這單號還在製作中，請稍候。`;
      }

      await fetch(
        `https://graph.facebook.com/v19.0/${env.PHONE_NUMBER_ID}/messages`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${env.WHATSAPP_TOKEN}`,
          },
          body: JSON.stringify({
            messaging_product: "whatsapp",
            to: from,
            type: "text",
            text: { body: reply },
          }),
        }
      );

      return new Response("OK");
    }

    return new Response("Method Not Allowed", { status: 405 });
  },
};
