package com.ordersphere.audit.config;

import com.ordersphere.audit.chain.AuditAnchor;
import com.ordersphere.audit.chain.LogAuditAnchor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnchorConfig {

  /** The default anchor. Declaring another AuditAnchor bean (e.g. a ledger client) replaces it. */
  @Bean
  @ConditionalOnMissingBean(AuditAnchor.class)
  public AuditAnchor auditAnchor() {
    return new LogAuditAnchor();
  }
}
