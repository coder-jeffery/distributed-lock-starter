package io.github.coderjeffery.distributedlock.demo;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestParam String orderId) {
        return Map.of("orderId", orderId, "seq", orderService.pay(orderId));
    }
}
