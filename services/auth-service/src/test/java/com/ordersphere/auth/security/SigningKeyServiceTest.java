package com.ordersphere.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.auth.domain.SigningKey;
import com.ordersphere.auth.repository.SigningKeyRepository;
import com.ordersphere.security.JwtProperties;
import com.ordersphere.security.JwtVerifier;
import com.ordersphere.security.RsaKeys;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigningKeyServiceTest {

  @Mock private SigningKeyRepository repository;

  @Test
  void generatesAndStoresAKeyOnFirstStart() {
    when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());
    when(repository.save(any(SigningKey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SigningKeyService service = new SigningKeyService(repository, "", "", "configured");

    assertThat(service.activeKey().kid()).isNotBlank();
    verify(repository).save(any(SigningKey.class));
  }

  @Test
  void reusesTheStoredKeySoTokensSurviveRestarts() throws Exception {
    KeyPair pair = newPair();
    SigningKey stored =
        new SigningKey(
            "kid-1", RsaKeys.encode(pair.getPublic()), RsaKeys.encode(pair.getPrivate()));
    when(repository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(stored));

    SigningKeyService service = new SigningKeyService(repository, "", "", "configured");

    assertThat(service.activeKey().kid()).isEqualTo("kid-1");
    assertThat(service.activeKey().publicKey()).isEqualTo(pair.getPublic());
    verify(repository, never()).save(any());
  }

  @Test
  void aConfiguredKeyPairWinsAndOlderStoredKeysStayPublished() throws Exception {
    KeyPair configured = newPair();
    KeyPair older = newPair();
    when(repository.findAllByOrderByCreatedAtDesc())
        .thenReturn(
            List.of(
                new SigningKey(
                    "old", RsaKeys.encode(older.getPublic()), RsaKeys.encode(older.getPrivate()))));

    SigningKeyService service =
        new SigningKeyService(
            repository,
            RsaKeys.encode(configured.getPrivate()),
            RsaKeys.encode(configured.getPublic()),
            "prod-2026");

    assertThat(service.activeKey().kid()).isEqualTo("prod-2026");
    assertThat(service.publicKeys()).containsOnlyKeys("prod-2026", "old");
    verify(repository, never()).save(any());
  }

  @Test
  void issuedTokensVerifyWithThePublishedKey() throws Exception {
    KeyPair pair = newPair();
    when(repository.findFirstByActiveTrueOrderByCreatedAtDesc())
        .thenReturn(
            Optional.of(
                new SigningKey(
                    "kid-1", RsaKeys.encode(pair.getPublic()), RsaKeys.encode(pair.getPrivate()))));
    SigningKeyService keys = new SigningKeyService(repository, "", "", "configured");
    JwtProperties properties = new JwtProperties();
    TokenIssuer issuer = new TokenIssuer(keys, properties, Duration.ofMinutes(5));

    String token = issuer.issueServiceToken("orders-service").token();

    JwtVerifier verifier =
        new JwtVerifier(kid -> keys.activeKey().publicKey(), properties.getIssuer());
    assertThat(verifier.verify(token).get("role", String.class)).isEqualTo("SERVICE");
  }

  private static KeyPair newPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
