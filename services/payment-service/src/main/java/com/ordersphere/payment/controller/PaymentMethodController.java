package com.ordersphere.payment.controller;

import com.ordersphere.payment.dto.CreatePaymentMethodRequest;
import com.ordersphere.payment.dto.PaymentMethodResponse;
import com.ordersphere.payment.service.PaymentMethodService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment-methods")
public class PaymentMethodController {

  private final PaymentMethodService paymentMethodService;

  public PaymentMethodController(PaymentMethodService paymentMethodService) {
    this.paymentMethodService = paymentMethodService;
  }

  @PostMapping
  public ResponseEntity<PaymentMethodResponse> createPaymentMethod(
      @Valid @RequestBody CreatePaymentMethodRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(paymentMethodService.createPaymentMethod(principal.getName(), request));
  }

  @GetMapping
  public List<PaymentMethodResponse> listPaymentMethods(Principal principal) {
    return paymentMethodService.listPaymentMethods(principal.getName());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePaymentMethod(@PathVariable Long id, Principal principal) {
    paymentMethodService.deletePaymentMethod(principal.getName(), id);
    return ResponseEntity.noContent().build();
  }
}
