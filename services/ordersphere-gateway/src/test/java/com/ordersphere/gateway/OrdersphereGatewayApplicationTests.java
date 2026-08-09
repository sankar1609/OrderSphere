package com.ordersphere.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
class OrdersphereGatewayApplicationTests {

  @Test
  void contextLoads() {}
}
