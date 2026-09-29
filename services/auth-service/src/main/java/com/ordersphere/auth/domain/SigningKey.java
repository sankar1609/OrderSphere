package com.ordersphere.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An RSA key pair (base64 DER: X.509 public, PKCS#8 private) used to sign JWTs. */
@Entity
@Table(name = "signing_keys")
@Getter
@NoArgsConstructor
public class SigningKey {

  @Id private String kid;

  @Column(name = "public_key", nullable = false)
  private String publicKey;

  @Column(name = "private_key", nullable = false)
  private String privateKey;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SigningKey(String kid, String publicKey, String privateKey) {
    this.kid = kid;
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.active = true;
    this.createdAt = Instant.now();
  }
}
