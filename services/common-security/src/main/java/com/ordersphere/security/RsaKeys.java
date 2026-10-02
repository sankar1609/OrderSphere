package com.ordersphere.security;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parsing and encoding helpers for the RSA keys that sign and verify OrderSphere JWTs. */
public final class RsaKeys {

  private RsaKeys() {}

  /** Accepts PEM ("-----BEGIN PUBLIC KEY-----" ...) or bare base64 of the X.509 DER encoding. */
  public static RSAPublicKey parsePublicKey(String encoded) {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(encoded)));
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("Not a valid RSA public key", ex);
    }
  }

  /** Accepts PEM ("-----BEGIN PRIVATE KEY-----" ...) or bare base64 of the PKCS#8 DER encoding. */
  public static RSAPrivateKey parsePrivateKey(String encoded) {
    try {
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der(encoded)));
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("Not a valid RSA private key", ex);
    }
  }

  /**
   * Base64 (no PEM armour) of the key's standard encoding - X.509 for public, PKCS#8 for private.
   */
  public static String encode(java.security.Key key) {
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }

  /** The key as a JWK (RFC 7517) entry for a JWKS document. */
  public static Map<String, Object> toJwk(String kid, RSAPublicKey key) {
    Map<String, Object> jwk = new LinkedHashMap<>();
    jwk.put("kty", "RSA");
    jwk.put("kid", kid);
    jwk.put("use", "sig");
    jwk.put("alg", "RS256");
    jwk.put("n", base64Url(key.getModulus()));
    jwk.put("e", base64Url(key.getPublicExponent()));
    return jwk;
  }

  /** Rebuilds a public key from a JWK's {@code n} and {@code e} members. */
  public static RSAPublicKey fromJwk(String n, String e) {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA")
              .generatePublic(
                  new RSAPublicKeySpec(
                      new BigInteger(1, Base64.getUrlDecoder().decode(n)),
                      new BigInteger(1, Base64.getUrlDecoder().decode(e))));
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("Not a valid RSA JWK", ex);
    }
  }

  private static byte[] der(String encoded) {
    String base64 = encoded.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }

  private static String base64Url(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      byte[] unsigned = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
      bytes = unsigned;
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
