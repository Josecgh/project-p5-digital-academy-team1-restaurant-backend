package dev.team1.payments;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.orders.OrderEntity;
import dev.team1.orders.OrderRepository;
import dev.team1.orders.OrderService;
import dev.team1.orders.dtos.OrderDTOResponse;
import dev.team1.payments.dtos.PaymentDTORequest;
import dev.team1.payments.dtos.PaymentDTOResponse;

// GS-341: integra la pasarela de pago Stripe para pagar con tarjeta los pedidos a domicilio.
// Crea la sesión de pago y, después, verifica con Stripe que el pedido se ha pagado de verdad.
@Service
public class PaymentService {

    private static final String CURRENCY = "eur";

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    // Los valores de Stripe vienen de application.properties (que los lee del fichero .env),
    // así la clave secreta nunca se sube al repositorio.
    @Value("${stripe.api.key:}")
    private String stripeApiKey;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    public PaymentService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    // 1. Crea la página de pago de Stripe para un pedido.
    // readOnly: solo leemos el pedido (y su usuario, que es LAZY); no modificamos nada en la BD.
    @Transactional(readOnly = true)
    public PaymentDTOResponse createCheckoutSession(PaymentDTORequest request) {
        // a) Buscamos el pedido en la BD: el importe sale de aquí y NO del frontend,
        //    para que el cliente no pueda cambiar el precio a pagar.
        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + request.orderId()));

        // b) Solo se paga con Stripe un pedido a domicilio con "Tarjeta online" que aún no esté pagado.
        if (order.getChannel() != OrderChannel.DOMICILIO
                || order.getPaymentMethod() != PaymentMethod.ONLINE_CARD) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Order must use online card payment for delivery");
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order cannot be paid from status: " + order.getStatus());
        }

        // Usuario autenticado: usamos el email de su cuenta. Invitado: el email que envía en la petición.
        String email = order.getUser() != null ? order.getUser().getEmail() : request.email();

        // c) Describimos lo que Stripe debe mostrar en la página de pago.
        //    Stripe sustituye {CHECKOUT_SESSION_ID} por el id real al volver al frontend,
        //    y clientReferenceId guarda nuestro id de pedido para recuperarlo al confirmar.
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(
                        SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(order.getId().toString())
                .setCustomerEmail(email == null || email.isBlank() ? null : email)
                .addLineItem(buildLineItem(order))
                .build();

        // d) Enviamos la sesión a Stripe. La clave se comprueba aquí, después de las validaciones,
        //    para que un pedido inexistente devuelva 404 aunque falte la clave.
        setStripeApiKey();
        try {
            Session session = Session.create(params);
            return new PaymentDTOResponse(
                    session.getId(),
                    session.getUrl(),
                    session.getPaymentStatus(),
                    order.getId(),
                    order.getTotal());
        } catch (StripeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to create the payment session", e);
        }
    }

    // 2. Después del pago, preguntamos a Stripe si de verdad se ha cobrado y marcamos el pedido como PAID.
    //    No nos fiamos del frontend: cualquiera podría abrir la URL de éxito sin haber pagado.
    public OrderDTOResponse confirmPayment(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Payment session ID is required");
        }

        setStripeApiKey();

        try {
            Session session = Session.retrieve(sessionId);

            if (!"paid".equals(session.getPaymentStatus())) {
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment not completed");
            }

            // Recuperamos el id del pedido que guardamos en la sesión al crearla.
            Long orderId;
            try {
                orderId = Long.valueOf(session.getClientReferenceId());
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Payment session has no valid order reference");
            }

            if (orderId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Payment session has no valid order reference");
            }

            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Order not found: " + orderId));

            if (order.getChannel() != OrderChannel.DOMICILIO
                    || order.getPaymentMethod() != PaymentMethod.ONLINE_CARD) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Payment session does not reference an online card order");
            }

            // Comprobamos que lo cobrado por Stripe coincide con el total del pedido (importe y moneda).
            Long paidAmount = session.getAmountTotal();
            long expectedAmount = toCents(order.getTotal());

            if (paidAmount == null
                    || paidAmount.longValue() != expectedAmount
                    || !CURRENCY.equals(session.getCurrency())
                    || !"payment".equals(session.getMode())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Payment session does not match the order");
            }

            // Reutilizamos la lógica existente de pedidos (PLACED -> PAID); si ya estaba pagado no hace nada.
            return orderService.markAsPaid(orderId);
        } catch (StripeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to verify the payment session", e);
        }
    }

    // La única línea que se muestra en la página de Stripe: "GitSushi - Order #5" con el total del pedido.
    private SessionCreateParams.LineItem buildLineItem(OrderEntity order) {
        SessionCreateParams.LineItem.PriceData.ProductData product =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("GitSushi - Order #" + order.getId())
                        .build();

        SessionCreateParams.LineItem.PriceData price =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(CURRENCY)
                        .setUnitAmount(toCents(order.getTotal()))
                        .setProductData(product)
                        .build();

        return SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(price)
                .build();
    }

    // Si falta la clave (por ejemplo, sin fichero .env) devolvemos 503 en vez de un error genérico.
    private void setStripeApiKey() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Online payment is not configured");
        }
        Stripe.apiKey = stripeApiKey;
    }

    // Stripe trabaja en céntimos: 12.50 € -> 1250
    private long toCents(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order total must be greater than zero");
        }

        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order total cannot be represented in cents");
        }
    }
}
