package com.ordersphere.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.payment.gateway.PaymentGatewayClient.SessionStatus;
import com.ordersphere.payment.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the payment provider's checkout outcomes. Not JWT-protected (the provider has no
 * OrderSphere identity); instead every request must carry an HMAC-SHA256 signature of the raw body
 * made with the shared webhook secret.
 */
@RestController
public class GatewayWebhookController {

  static final String SIGNATURE_HEADER = "X-Dummy-Gateway-Signature";

  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;
  private final byte[] webhookSecret;

  public GatewayWebhookController(
      PaymentService paymentService,
      ObjectMapper objectMapper,
      @Value("${payment.gateway.webhook-secret}") String webhookSecret) {
    this.paymentService = paymentService;
    this.objectMapper = objectMapper;
    this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
  }

  @PostMapping("/payments/webhooks/gateway")
  public ResponseEntity<Void> receive(
      @RequestBody String body,
      @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
    if (signature == null
        || !MessageDigest.isEqual(
            sign(body).getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8))) {
      return ResponseEntity.status(401).build();
    }

    GatewayEvent event;
    try {
      event = objectMapper.readValue(body, GatewayEvent.class);
    } catch (JsonProcessingException ex) {
      return ResponseEntity.badRequest().build();
    }

    SessionStatus status =
        switch (event.type() == null ? "" : event.type()) {
          case "checkout.session.completed" -> SessionStatus.SUCCEEDED;
          case "checkout.session.cancelled" -> SessionStatus.CANCELLED;
          default -> null;
        };
    if (status != null && event.sessionId() != null) {
      paymentService.applySessionOutcome(event.sessionId(), status, event.chargeReference());
    }
    return ResponseEntity.noContent().build();
  }

  private String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
      return "sha256="
          + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("Unable to verify webhook signature", ex);
    }
  }

  record GatewayEvent(String type, String sessionId, String chargeReference) {}
}
