package com.ordersphere.payment_service;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public boolean processPayment(Long orderId) {

        System.out.println(
                "Processing payment for order: "
                        + orderId
        );

        return true; // mock success
    }
}
