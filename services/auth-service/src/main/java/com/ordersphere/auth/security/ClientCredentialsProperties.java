package com.ordersphere.auth.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Services allowed to obtain a SERVICE-role token via client credentials: {@code
 * auth.clients.<client-id>.secret}. Secrets come from the environment.
 */
@Component
@ConfigurationProperties(prefix = "auth")
public class ClientCredentialsProperties {

  private Map<String, Client> clients = new LinkedHashMap<>();

  public Map<String, Client> getClients() {
    return clients;
  }

  public void setClients(Map<String, Client> clients) {
    this.clients = clients;
  }

  public static class Client {
    private String secret;

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }
  }
}
