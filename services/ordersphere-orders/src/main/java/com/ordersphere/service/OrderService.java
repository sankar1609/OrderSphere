package com.ordersphere.service;

import com.ordersphere.events.OrderCreatedEvent;
//import com.ordersphere.client.InventoryClient;
import com.ordersphere.dto.CreateOrderRequest;

import com.ordersphere.entity.Order;
import com.ordersphere.event.OrderEventProducer;
import com.ordersphere.exceptions.OutOfStockException;
import com.ordersphere.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    //private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    public OrderService(OrderRepository orderRepository, OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
    }

    public Order createOrder(CreateOrderRequest request, String username) {


        Order order = new Order(request.productId(), request.quantity(), username, "CREATED");

        orderRepository.save(order);
        OrderCreatedEvent event = new OrderCreatedEvent();

        event.setOrderId(order.getId());
        event.setProductId(order.getProductId());
        event.setQuantity(order.getQuantity());
        event.setUsername(username);

        orderEventProducer.publishOrderCreated(event);
        return order;
    }

    // New helper to update an order status safely
    public void updateOrderStatus(Long orderId, String status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
        });
    }
}
