package com.ordersphere.dummygateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Notifies the merchant of a completed/cancelled checkout, signing the raw body with HMAC-SHA256 in
 * {@value #SIGNATURE_HEADER}. Delivery is best-effort (one attempt): merchants are expected to
 * reconcile by polling the session, exactly as with a real provider.
 */
@Component
public class WebhookSender {

  public static final String SIGNATURE_HEADER = "X-Dummy-Gateway-Signature";

  private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final GatewayProperties properties;
  private final TaskExecutor executor;

  public WebhookSender(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      GatewayProperties properties,
      TaskExecutor executor) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.executor = executor;
  }

  /**
   * Builds and signs the event now, delivers it in the background - like a real provider, the
   * customer's payment never waits on the merchant's endpoint (and the session's row lock isn't
   * held while it does). Delivery has short timeouts (see WebhookClientConfig).
   */
  public void send(CheckoutSession session, String type) {
    if (session.getWebhookUrl() == null || session.getWebhookUrl().isBlank()) {
      return;
    }
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", type);
    event.put("sessionId", session.getId());
    event.put("merchantReference", session.getMerchantReference());
    event.put("chargeReference", session.getChargeReference());
    event.put("amount", session.getAmount());
    event.put("currency", session.getCurrency());
    String body;
    try {
      body = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException ex) {
      log.warn("Webhook {} for session {} not sent: {}", type, session.getId(), ex.getMessage());
      return;
    }
    String url = session.getWebhookUrl();
    String sessionId = session.getId();
    String signature = sign(body, properties.webhookSecret());
    executor.execute(() -> deliver(url, body, signature, type, sessionId));
  }

  private void deliver(String url, String body, String signature, String type, String sessionId) {
    try {
      restClient
          .post()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .header(SIGNATURE_HEADER, signature)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException ex) {
      log.warn("Webhook {} for session {} to {} failed: {}", type, sessionId, url, ex.getMessage());
    }
  }

  /** "sha256=" + lowercase hex HMAC-SHA256 of the body. */
  public static String sign(String body, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return "sha256="
          + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("Unable to sign webhook", ex);
    }
  }
}
