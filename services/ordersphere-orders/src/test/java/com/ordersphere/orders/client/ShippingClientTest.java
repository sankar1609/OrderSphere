package com.ordersphere.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ordersphere.orders.exception.ShipmentCreationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ShippingClientTest {

  @Test
  void createShipmentSendsExpectedRequestAndReturnsShipmentId() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ShippingClient client = new ShippingClient(builder);

    server
        .expect(requestTo("http://shipping-service/shipments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andExpect(jsonPath("$.orderId").value(1))
        .andExpect(jsonPath("$.destination").value("1 Test Way"))
        .andRespond(
            withSuccess("{\"id\": 7, \"status\": \"CREATED\"}", MediaType.APPLICATION_JSON));

    Long shipmentId = client.createShipment(1L, "1 Test Way", "Bearer token");

    assertThat(shipmentId).isEqualTo(7L);
    server.verify();
  }

  @Test
  void createShipmentThrowsShipmentCreationExceptionOnFailure() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ShippingClient client = new ShippingClient(builder);

    server.expect(requestTo("http://shipping-service/shipments")).andRespond(withServerError());

    assertThatThrownBy(() -> client.createShipment(1L, "1 Test Way", "Bearer token"))
        .isInstanceOf(ShipmentCreationException.class);
  }
}
