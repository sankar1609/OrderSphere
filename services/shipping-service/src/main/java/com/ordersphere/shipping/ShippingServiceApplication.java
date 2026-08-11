package com.ordersphere.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShippingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ShippingServiceApplication.class, args);
  }
}
