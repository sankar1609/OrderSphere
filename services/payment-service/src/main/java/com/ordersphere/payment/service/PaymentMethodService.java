package com.ordersphere.payment.service;

import com.ordersphere.payment.domain.PaymentMethod;
import com.ordersphere.payment.dto.CreatePaymentMethodRequest;
import com.ordersphere.payment.dto.PaymentMethodResponse;
import com.ordersphere.payment.exception.PaymentMethodNotFoundException;
import com.ordersphere.payment.repository.PaymentMethodRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

  private final PaymentMethodRepository paymentMethodRepository;

  public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
    this.paymentMethodRepository = paymentMethodRepository;
  }

  public PaymentMethodResponse createPaymentMethod(
      String username, CreatePaymentMethodRequest request) {
    PaymentMethod method = new PaymentMethod(username, request.type(), request.token());
    return PaymentMethodResponse.from(paymentMethodRepository.save(method));
  }

  @Transactional(readOnly = true)
  public List<PaymentMethodResponse> listPaymentMethods(String username) {
    return paymentMethodRepository.findByCustomerUsername(username).stream()
        .map(PaymentMethodResponse::from)
        .toList();
  }

  public void deletePaymentMethod(String username, Long id) {
    PaymentMethod method =
        paymentMethodRepository
            .findByIdAndCustomerUsername(id, username)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    paymentMethodRepository.delete(method);
  }
}
