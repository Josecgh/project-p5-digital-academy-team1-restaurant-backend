package dev.team1.payments;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.team1.enums.OrderChannel;
import dev.team1.enums.PaymentMethod;

/** Central source of truth for payment methods supported by each order channel. */
@Service
public class PaymentChannelRulesService {

    private static final Map<OrderChannel, List<PaymentMethod>> ALLOWED_PAYMENT_METHODS = Map.of(
            OrderChannel.SALA, List.of(PaymentMethod.CASH_ONSITE, PaymentMethod.CARD_ONSITE),
            OrderChannel.DOMICILIO, List.of(PaymentMethod.ONLINE_CARD, PaymentMethod.CASH_ON_DELIVERY));

    public List<PaymentMethod> getAllowedPaymentMethods(OrderChannel channel) {
        return ALLOWED_PAYMENT_METHODS.getOrDefault(channel, List.of());
    }

    public void validate(OrderChannel channel, PaymentMethod paymentMethod) {
        if (!getAllowedPaymentMethods(channel).contains(paymentMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment method " + paymentMethod + " is not allowed for channel " + channel);
        }
    }
}
