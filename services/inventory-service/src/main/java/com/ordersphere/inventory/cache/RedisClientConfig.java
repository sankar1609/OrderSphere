package com.ordersphere.inventory.cache;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisClientConfig {

  /**
   * While Redis is unreachable, fail a command at once instead of queueing it until the command
   * timeout: the cache is optional, so a request shouldn't wait on it (see CatalogCache).
   */
  @Bean
  public LettuceClientConfigurationBuilderCustomizer rejectCommandsWhileDisconnected() {
    return builder ->
        builder.clientOptions(
            ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
  }
}
