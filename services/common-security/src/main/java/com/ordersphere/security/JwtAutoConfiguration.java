package com.ordersphere.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
    return new JwtTokenProvider(properties.getSecret(), properties.getExpirationMillis());
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    return new JwtAuthenticationFilter(jwtTokenProvider);
  }
}
