package dev.team1.orders.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

import dev.team1.enums.OrderChannel;
import dev.team1.enums.PaymentMethod;

// order creation request with cart items and an optional chef note.
public record OrderDTORequest(
                @NotEmpty List<@NotNull @Valid OrderItemDTORequest> items,
                @Size (max = 500 , message = "Chef note must not exceed 500 characters") 
                String chefNote,
                @NotNull OrderChannel channel,
                @NotNull PaymentMethod paymentMethod,
                String deliveryAddress,
                Integer tableNumber) {

        // Keep the existing constructor for callers that do not send channel-specific details.
        public OrderDTORequest(List<@NotNull @Valid OrderItemDTORequest> items,
                        String chefNote,
                        OrderChannel channel,
                        PaymentMethod paymentMethod) {
                this(items, chefNote, channel, paymentMethod, null, null);
        }

        // one ordered product and its quantity.
        public record OrderItemDTORequest(
                        @NotNull @Positive Long productId,
                        @NotNull @Positive Integer quantity) {
        }
}
