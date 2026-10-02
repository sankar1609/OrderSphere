package com.ordersphere.dummygateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * A stand-in for an external card payment provider (think Stripe Checkout). It is deliberately not
 * part of the OrderSphere mesh - no Eureka, no JWT, no database - because a real provider wouldn't
 * be either: payment-service talks to it over its merchant API like any third party.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DummyPaymentGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(DummyPaymentGatewayApplication.class, args);
  }
}
