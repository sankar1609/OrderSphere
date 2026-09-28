package com.ordersphere.dummygateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
    String publicUrl, String apiKey, String webhookSecret, Duration sessionTtl) {}
