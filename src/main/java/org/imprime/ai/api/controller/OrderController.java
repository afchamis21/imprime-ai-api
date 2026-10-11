package org.imprime.ai.api.controller;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/makers")
    public ResponseEntity<?> getMakers(@RequestParam String orderGuid, @RequestParam(value = "max", required = false, defaultValue = "10") Integer max) {
        return BaseResponse.build(orderService.getMakerOptions(orderGuid, max), HttpStatus.OK);
    }
}
