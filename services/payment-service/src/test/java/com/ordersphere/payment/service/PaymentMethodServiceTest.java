package com.ordersphere.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.payment.domain.PaymentMethod;
import com.ordersphere.payment.domain.PaymentMethodType;
import com.ordersphere.payment.dto.CreatePaymentMethodRequest;
import com.ordersphere.payment.dto.PaymentMethodResponse;
import com.ordersphere.payment.exception.PaymentMethodNotFoundException;
import com.ordersphere.payment.repository.PaymentMethodRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

  @Mock private PaymentMethodRepository paymentMethodRepository;

  private PaymentMethodService paymentMethodService;

  @BeforeEach
  void setUp() {
    paymentMethodService = new PaymentMethodService(paymentMethodRepository);
  }

  @Test
  void createPaymentMethodSavesAndReturnsResponse() {
    when(paymentMethodRepository.save(org.mockito.ArgumentMatchers.any(PaymentMethod.class)))
        .thenAnswer(
            invocation -> {
              PaymentMethod method = invocation.getArgument(0);
              method.setId(1L);
              return method;
            });

    PaymentMethodResponse response =
        paymentMethodService.createPaymentMethod(
            "alice", new CreatePaymentMethodRequest(PaymentMethodType.CARD, "tok-123"));

    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.token()).isEqualTo("tok-123");
    assertThat(response.type()).isEqualTo(PaymentMethodType.CARD);
  }

  @Test
  void listPaymentMethodsReturnsOnlyCallersMethods() {
    PaymentMethod method = new PaymentMethod("alice", PaymentMethodType.CARD, "tok-123");
    when(paymentMethodRepository.findByCustomerUsername("alice")).thenReturn(List.of(method));

    List<PaymentMethodResponse> responses = paymentMethodService.listPaymentMethods("alice");

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).token()).isEqualTo("tok-123");
  }

  @Test
  void deletePaymentMethodRemovesOwnedMethod() {
    PaymentMethod method = new PaymentMethod("alice", PaymentMethodType.CARD, "tok-123");
    when(paymentMethodRepository.findByIdAndCustomerUsername(1L, "alice"))
        .thenReturn(Optional.of(method));

    paymentMethodService.deletePaymentMethod("alice", 1L);

    verify(paymentMethodRepository).delete(method);
  }

  @Test
  void deletePaymentMethodRejectsNonOwner() {
    when(paymentMethodRepository.findByIdAndCustomerUsername(1L, "bob"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentMethodService.deletePaymentMethod("bob", 1L))
        .isInstanceOf(PaymentMethodNotFoundException.class);
  }
}
