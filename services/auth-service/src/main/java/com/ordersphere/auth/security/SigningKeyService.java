package com.ordersphere.auth.security;

import com.ordersphere.auth.domain.SigningKey;
import com.ordersphere.auth.repository.SigningKeyRepository;
import com.ordersphere.security.RsaKeys;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the RSA keys auth-service signs JWTs with. A key pair supplied through {@code
 * jwt.signing.private-key}/{@code public-key} (e.g. from a secret store) wins; otherwise the newest
 * key in {@code signing_keys} is used, generating and storing one on first start.
 */
@Service
public class SigningKeyService {

  private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);

  private final SigningKeyRepository repository;
  private final String configuredPrivateKey;
  private final String configuredPublicKey;
  private final String configuredKeyId;

  private volatile ActiveKey activeKey;

  public SigningKeyService(
      SigningKeyRepository repository,
      @Value("${jwt.signing.private-key:}") String configuredPrivateKey,
      @Value("${jwt.signing.public-key:}") String configuredPublicKey,
      @Value("${jwt.signing.key-id:configured}") String configuredKeyId) {
    this.repository = repository;
    this.configuredPrivateKey = configuredPrivateKey;
    this.configuredPublicKey = configuredPublicKey;
    this.configuredKeyId = configuredKeyId;
  }

  /** The key new tokens are signed with. */
  public ActiveKey activeKey() {
    ActiveKey key = activeKey;
    if (key == null) {
      synchronized (this) {
        if (activeKey == null) {
          activeKey = loadOrCreate();
        }
        key = activeKey;
      }
    }
    return key;
  }

  /** Every public key a still-valid token may have been signed with, by key id. */
  @Transactional(readOnly = true)
  public Map<String, RSAPublicKey> publicKeys() {
    Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
    ActiveKey active = activeKey();
    keys.put(active.kid(), active.publicKey());
    for (SigningKey stored : repository.findAllByOrderByCreatedAtDesc()) {
      keys.putIfAbsent(stored.getKid(), RsaKeys.parsePublicKey(stored.getPublicKey()));
    }
    return keys;
  }

  private ActiveKey loadOrCreate() {
    if (StringUtils.hasText(configuredPrivateKey) && StringUtils.hasText(configuredPublicKey)) {
      log.info("Signing JWTs with the configured key '{}'", configuredKeyId);
      return new ActiveKey(
          configuredKeyId,
          RsaKeys.parsePrivateKey(configuredPrivateKey),
          RsaKeys.parsePublicKey(configuredPublicKey));
    }
    SigningKey stored =
        repository.findFirstByActiveTrueOrderByCreatedAtDesc().orElseGet(this::generateAndStore);
    return new ActiveKey(
        stored.getKid(),
        RsaKeys.parsePrivateKey(stored.getPrivateKey()),
        RsaKeys.parsePublicKey(stored.getPublicKey()));
  }

  private SigningKey generateAndStore() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      SigningKey key =
          new SigningKey(
              UUID.randomUUID().toString(),
              RsaKeys.encode(pair.getPublic()),
              RsaKeys.encode(pair.getPrivate()));
      log.info("Generated a new JWT signing key '{}'", key.getKid());
      return repository.save(key);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("RSA not available", ex);
    }
  }

  public record ActiveKey(String kid, RSAPrivateKey privateKey, RSAPublicKey publicKey) {}
}
