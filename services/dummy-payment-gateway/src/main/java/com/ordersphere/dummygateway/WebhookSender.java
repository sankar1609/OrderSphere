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

  public WebhookSender(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      GatewayProperties properties) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

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
    try {
      String body = objectMapper.writeValueAsString(event);
      restClient
          .post()
          .uri(session.getWebhookUrl())
          .contentType(MediaType.APPLICATION_JSON)
          .header(SIGNATURE_HEADER, sign(body, properties.webhookSecret()))
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (JsonProcessingException | RestClientException ex) {
      log.warn(
          "Webhook {} for session {} to {} failed: {}",
          type,
          session.getId(),
          session.getWebhookUrl(),
          ex.getMessage());
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
