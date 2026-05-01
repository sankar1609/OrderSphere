package com.ordersphere.service;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public String sendNotification(Long orderId) {

        String message =
                "Order " + orderId +
                        " shipped successfully.";

        System.out.println(
                "Sending notification: " + message
        );

        return message;
    }
}
