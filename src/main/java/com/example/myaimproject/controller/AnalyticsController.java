package com.example.myaimproject.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.myaimproject.entity.OrderEntity;
import com.example.myaimproject.repository.OrderRepository;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final OrderRepository orderRepository;

    public AnalyticsController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping(
            value = "/orders",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<OrderEntity>> getAllOrders() {

        List<OrderEntity> orders = orderRepository.findAll();

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(orders);
    }
}