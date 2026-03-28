/*
package com.ordersphere.controller;


import com.ordersphere.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reduce/{productId}")
    public ResponseEntity<String> reduceStock(@PathVariable Long productId) {
        return ResponseEntity.ok("Stock reduced for product " + productId);
    }
}*/
