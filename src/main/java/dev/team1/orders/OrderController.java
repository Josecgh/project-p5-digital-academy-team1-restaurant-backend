package dev.team1.orders;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import dev.team1.auth.CustomUserDetails;
import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.orders.dtos.OrderDTORequest;
import dev.team1.orders.dtos.OrderDTOResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "${api-endpoint}/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    
    public ResponseEntity<OrderDTOResponse> createOrder(
            @Valid @RequestBody OrderDTORequest request,
            @RequestHeader(value = "Device-Identifier", required = false) String deviceIdentifier,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        UUID userId = currentUser == null ? null : currentUser.user().getId();

        OrderDTOResponse response = orderService.createOrder(request, deviceIdentifier, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/paid")
    public ResponseEntity<OrderDTOResponse> markAsPaid(
            @PathVariable Long id) {
        OrderDTOResponse response = orderService.markAsPaid(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTOResponse> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<OrderDTOResponse>> getByStatus(
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.getByStatus(status));
    }

    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethod>> getPaymentMethods(
            @RequestParam OrderChannel channel) {
        return ResponseEntity.ok(orderService.getAllowedPaymentMethods(channel));
    }

}
