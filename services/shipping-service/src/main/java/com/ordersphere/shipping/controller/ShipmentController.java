package com.ordersphere.shipping.controller;

import com.ordersphere.shipping.dto.CreateShipmentRequest;
import com.ordersphere.shipping.dto.ReturnShipmentRequest;
import com.ordersphere.shipping.dto.ShipmentResponse;
import com.ordersphere.shipping.dto.TrackingEventResponse;
import com.ordersphere.shipping.service.ShipmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

  private final ShipmentService shipmentService;

  public ShipmentController(ShipmentService shipmentService) {
    this.shipmentService = shipmentService;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ShipmentResponse> createShipment(
      @Valid @RequestBody CreateShipmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createShipment(request));
  }

  @GetMapping("/{id}")
  public ShipmentResponse getShipment(@PathVariable Long id) {
    return shipmentService.getShipment(id);
  }

  @GetMapping("/{id}/tracking")
  public List<TrackingEventResponse> getTracking(@PathVariable Long id) {
    return shipmentService.getTracking(id);
  }

  @GetMapping("/order/{orderId}")
  public List<ShipmentResponse> listByOrder(@PathVariable Long orderId) {
    return shipmentService.listByOrder(orderId);
  }

  @PostMapping("/{id}/return")
  public ShipmentResponse requestReturn(
      @PathVariable Long id, @Valid @RequestBody ReturnShipmentRequest request) {
    return shipmentService.requestReturn(id, request);
  }
}
