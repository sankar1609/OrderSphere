package com.ordersphere.service;

import org.springframework.stereotype.Service;

@Service
public class ShippingService {

    public boolean createShipment(Long orderId) {

        System.out.println(
                "Creating shipment for order: "
                        + orderId
        );

        return true; // mock success
    }
}
