package com.ordersphere.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import java.security.Key;
import java.security.interfaces.RSAPublicKey;
import java.util.function.Function;

/**
 * Verifies OrderSphere JWTs. Only auth-service holds the private key that signs them; every other
 * service verifies with the public key, looked up by the token's {@code kid} header - so no service
 * can mint a token, unlike the old shared HMAC secret.
 */
public class JwtVerifier {

  private final io.jsonwebtoken.JwtParser parser;

  /**
   * @param keyForKid public key for a key id, or {@code null} if unknown
   * @param issuer the required {@code iss} claim
   */
  public JwtVerifier(Function<String, RSAPublicKey> keyForKid, String issuer) {
    this.parser =
        Jwts.parser()
            .keyLocator(
                new LocatorAdapter<Key>() {
                  @Override
                  protected Key locate(ProtectedHeader header) {
                    String kid = header.getKeyId();
                    RSAPublicKey key = kid == null ? null : keyForKid.apply(kid);
                    if (key == null) {
                      throw new JwtException("Unknown signing key id: " + kid);
                    }
                    return key;
                  }
                })
            .requireIssuer(issuer)
            .build();
  }

  /** Returns the claims of a valid, unexpired token signed by a known key; throws otherwise. */
  public Claims verify(String token) {
    return parser.parseSignedClaims(token).getPayload();
  }
}
