package com.ordersphere.auth.controller;

import com.ordersphere.auth.security.ClientCredentialsProperties;
import com.ordersphere.auth.security.SigningKeyService;
import com.ordersphere.auth.security.TokenIssuer;
import com.ordersphere.security.RsaKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token endpoints for other services: the public keys to verify OrderSphere JWTs with, and the
 * OAuth2 client-credentials grant that gives a service its own SERVICE-role token.
 */
@Tag(name = "Tokens", description = "Public signing keys and service (client-credentials) tokens")
@RestController
public class TokenController {

  private final SigningKeyService signingKeys;
  private final TokenIssuer tokenIssuer;
  private final ClientCredentialsProperties clients;

  public TokenController(
      SigningKeyService signingKeys, TokenIssuer tokenIssuer, ClientCredentialsProperties clients) {
    this.signingKeys = signingKeys;
    this.tokenIssuer = tokenIssuer;
    this.clients = clients;
  }

  @Operation(
      summary = "Public signing keys (JWKS)",
      description = "RS256 public keys services use to verify tokens.")
  @GetMapping(value = "/auth/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> jwks() {
    List<Map<String, Object>> keys =
        signingKeys.publicKeys().entrySet().stream()
            .map(entry -> RsaKeys.toJwk(entry.getKey(), entry.getValue()))
            .toList();
    return Map.of("keys", keys);
  }

  /**
   * RFC 6749 section 4.4: {@code grant_type=client_credentials}, client authenticated with HTTP
   * Basic (client id / secret).
   */
  @Operation(
      summary = "Service token (OAuth2 client credentials)",
      description =
          "Form body grant_type=client_credentials; client id and secret as HTTP Basic. Returns a 5-minute SERVICE-role token.")
  @PostMapping(value = "/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Map<String, Object>> token(
      @RequestParam(value = "grant_type", required = false) String grantType,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    String clientId = authenticatedClient(authorization);
    if (clientId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ordersphere\"")
          .body(Map.of("error", "invalid_client"));
    }
    if (!"client_credentials".equals(grantType)) {
      return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
    }
    TokenIssuer.IssuedToken issued = tokenIssuer.issueServiceToken(clientId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(
            Map.of(
                "access_token", issued.token(),
                "token_type", "Bearer",
                "expires_in", issued.expiresInSeconds()));
  }

  /** The client id if the Basic credentials match a configured client, otherwise null. */
  private String authenticatedClient(String authorization) {
    if (authorization == null || !authorization.startsWith("Basic ")) {
      return null;
    }
    String decoded;
    try {
      decoded =
          new String(
              Base64.getDecoder().decode(authorization.substring("Basic ".length()).trim()),
              StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    int colon = decoded.indexOf(':');
    if (colon < 0) {
      return null;
    }
    String clientId = decoded.substring(0, colon);
    String secret = decoded.substring(colon + 1);
    ClientCredentialsProperties.Client client = clients.getClients().get(clientId);
    if (client == null || client.getSecret() == null || client.getSecret().isEmpty()) {
      return null;
    }
    boolean matches =
        MessageDigest.isEqual(
            client.getSecret().getBytes(StandardCharsets.UTF_8),
            secret.getBytes(StandardCharsets.UTF_8));
    return matches ? clientId : null;
  }
}
