package dev.team1.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import dev.team1.contracts.IInvoiceService;
import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.enums.PaymentStatus;
import dev.team1.orders.dtos.KitchenOrderDTOResponse;
import dev.team1.orders.dtos.OrderDTORequest;
import dev.team1.orders.dtos.OrderDTOResponse;
import dev.team1.orders_products.OrderProductEntity;
import dev.team1.orders.dtos.KitchenMetricsDTOResponse;
import dev.team1.orders.dtos.DeliveryMetricsDTOResponse;
import dev.team1.orders.dtos.DeliveryConfirmationDTORequest;
import dev.team1.products.ProductEntity;
import dev.team1.products.ProductRepository;
import dev.team1.tables.TableEntity;
import dev.team1.tables.TableRepository;
import dev.team1.users.UserEntity;
import dev.team1.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    private static final UUID REGISTERED_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private UserRepository userRepository;

        @Mock
        private IInvoiceService invoiceService;

    @InjectMocks
    private OrderService service;

    @BeforeEach
    void setUpUser() {
        UserEntity user = new UserEntity();
        user.setId(REGISTERED_USER_ID);
        user.setAddress("Registered delivery address");
        lenient().when(userRepository.findById(REGISTERED_USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void createOrderWithoutDiscountCalculatesTotalsAndSavesOrderLines() {
        ProductEntity product = product(null);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(tableRepository.findByDeviceIdentifier("tablet-12")).thenReturn(Optional.of(table(12)));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 2)),
                "No onions", OrderChannel.ONSITE, PaymentMethod.CARD_ONSITE);

        OrderDTOResponse response = service.createOrder(request, "tablet-12", REGISTERED_USER_ID);

        assertEquals(new BigDecimal("20.00"), response.subtotal());
        assertEquals(new BigDecimal("0.00"), response.discountAmount());
        assertEquals(new BigDecimal("2.00"), response.vatAmount());
        assertEquals(new BigDecimal("22.00"), response.total());
        assertEquals(12, response.tableNumber());
        assertEquals(OrderStatus.PLACED, response.status());
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        OrderEntity savedOrder = captor.getValue();
        assertEquals("No onions", savedOrder.getChefNote());
        assertEquals(OrderChannel.ONSITE, savedOrder.getChannel());
        assertEquals(PaymentMethod.CARD_ONSITE, savedOrder.getPaymentMethod());
        assertEquals(1, savedOrder.getOrderProducts().size());
        assertSame(product, savedOrder.getOrderProducts().get(0).getProduct());
        assertSame(savedOrder, savedOrder.getOrderProducts().get(0).getOrder());
        assertEquals(new BigDecimal("2"), savedOrder.getOrderProducts().get(0).getQuantity());
    }

    @Test
    void createOrderAppliesDiscountBeforeVat() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(new BigDecimal("10"))));
        when(tableRepository.findByDeviceIdentifier("tablet-12")).thenReturn(Optional.of(table(12)));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 2)),
                null, OrderChannel.ONSITE, PaymentMethod.CASH_ONSITE);

        OrderDTOResponse response = service.createOrder(request, "tablet-12", REGISTERED_USER_ID);

        assertEquals(new BigDecimal("20.00"), response.subtotal());
        assertEquals(new BigDecimal("2.00"), response.discountAmount());
        assertEquals(new BigDecimal("1.80"), response.vatAmount());
        assertEquals(new BigDecimal("19.80"), response.total());
        assertEquals(OrderStatus.PLACED, response.status());
        assertEquals(OrderChannel.ONSITE, response.channel());
        assertEquals(PaymentMethod.CASH_ONSITE, response.paymentMethod());
    }

    @Test
    void createOnsiteOrderRejectsAbsentDeviceIdentifier() {
        OrderDTORequest request = onsiteRequest();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(request, null, REGISTERED_USER_ID));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void createOnsiteOrderRejectsBlankDeviceIdentifier() {
        OrderDTORequest request = onsiteRequest();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(request, "   ", REGISTERED_USER_ID));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void createOnsiteOrderRejectsUnknownDeviceWithoutSaving() {
        when(tableRepository.findByDeviceIdentifier("unknown-device"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(onsiteRequest(), "unknown-device", REGISTERED_USER_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void createOnlineOrderDoesNotAssociateTable() {
        ProductEntity product = product(null);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONLINE, PaymentMethod.CASH_ON_DELIVERY);

        OrderDTOResponse response = service.createOrder(request, null, REGISTERED_USER_ID);

        assertEquals(OrderChannel.ONLINE, response.channel());
        assertEquals(null, response.tableNumber());
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(null, captor.getValue().getTable());
        assertSame(userRepository.findById(REGISTERED_USER_ID).orElseThrow(), captor.getValue().getUser());
    }

    @Test
    void createOrderAssociatesAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setAddress("42 Example Street");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(null)));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONLINE, PaymentMethod.ONLINE_CARD);

        service.createOrder(request, null, userId);

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        assertSame(user, captor.getValue().getUser());
        assertEquals("42 Example Street", captor.getValue().getDeliveryAddress());
    }

    @Test
    void createOrderRejectsMissingAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONLINE, PaymentMethod.ONLINE_CARD);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(request, null, userId));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(orderRepository, productRepository, tableRepository);
    }

    @Test
    void deliveryOrderRequiresAccount() {
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONLINE, PaymentMethod.ONLINE_CARD);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(request, "tablet-12", null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(userRepository, orderRepository, productRepository, tableRepository);
    }

    @Test
    void deliveryOrderRequiresAddress() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(new UserEntity()));
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONLINE, PaymentMethod.ONLINE_CARD);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(request, null, userId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository, tableRepository, orderRepository);
    }

    @Test
    void onsiteOrderAllowsGuest() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(null)));
        when(tableRepository.findByDeviceIdentifier("tablet-12")).thenReturn(Optional.of(table(12)));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));

        OrderDTOResponse response = service.createOrder(onsiteRequest(), "tablet-12", null);

        assertEquals(OrderChannel.ONSITE, response.channel());
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        assertNull(captor.getValue().getUser());
        assertNull(captor.getValue().getDeliveryAddress());
        assertEquals(12, captor.getValue().getTable().getTableNumber());
    }

    @ParameterizedTest
    @CsvSource({
            "CASH_ONSITE, PENDING_CASH",
            "CARD_ONSITE, PENDING_CARD_TERMINAL"
    })
    void markAsPaidClearsPendingPaymentStatus(
            PaymentMethod paymentMethod, PaymentStatus initialPaymentStatus) {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PLACED);
        order.setChannel(OrderChannel.ONSITE);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(initialPaymentStatus);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderDTOResponse response = service.markAsPaid(1L);

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(OrderStatus.PAID, response.status());
        assertNull(order.getPaymentStatus());
        assertNull(response.paymentStatus());
        assertEquals(paymentMethod, order.getPaymentMethod());
        verify(orderRepository).save(order);
                verify(invoiceService).createForPaidOrder(order);
    }

        @Test
        void markAsPaidEnsuresInvoiceForAlreadyPaidOrder() {
                OrderEntity order = new OrderEntity();
                order.setStatus(OrderStatus.PAID);
                order.setChannel(OrderChannel.ONLINE);
                order.setPaymentMethod(PaymentMethod.ONLINE_CARD);
                when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

                OrderDTOResponse response = service.markAsPaid(1L);

                assertEquals(OrderStatus.PAID, response.status());
                verify(orderRepository, never()).save(any(OrderEntity.class));
                verify(invoiceService).createForPaidOrder(order);
        }

    @Test
    void markAsPaidRejectsDeliveredOrderWithoutSaving() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsPaid(1L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @ParameterizedTest
    @CsvSource({
            "CASH_ONSITE, PENDING_CASH",
            "CARD_ONSITE, PENDING_CARD_TERMINAL"
    })
    void createOnsiteOrderSavesAndReturnsPendingPaymentStatus(
            PaymentMethod paymentMethod, PaymentStatus expectedPaymentStatus) {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(null)));
        when(tableRepository.findByDeviceIdentifier("tablet-12"))
                .thenReturn(Optional.of(table(12)));
        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(call -> call.getArgument(0));

        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONSITE, paymentMethod);

        OrderDTOResponse response = service.createOrder(request, "tablet-12", REGISTERED_USER_ID);

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        OrderEntity savedOrder = captor.getValue();

        assertEquals(paymentMethod, savedOrder.getPaymentMethod());
        assertEquals(expectedPaymentStatus, savedOrder.getPaymentStatus());
        assertEquals(OrderStatus.PLACED, savedOrder.getStatus());
        assertEquals(expectedPaymentStatus, response.paymentStatus());
        assertEquals(OrderStatus.PLACED, response.status());
    }

    @ParameterizedTest
    @CsvSource({
            "CASH_ONSITE, PENDING_CASH",
            "CARD_ONSITE, PENDING_CARD_TERMINAL"
    })
    void getActiveKitchenOrdersReturnsPendingPaymentStatus(
            PaymentMethod paymentMethod, PaymentStatus expectedPaymentStatus) {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PLACED);
        order.setChannel(OrderChannel.ONSITE);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(expectedPaymentStatus);
        order.setCreatedAt(LocalDateTime.now());
        order.setOrderProducts(new ArrayList<>());

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.DELAYED);
        when(orderRepository.findByStatusIn(activeStatuses)).thenReturn(List.of(order));

        List<KitchenOrderDTOResponse> responses = service.getActiveKitchenOrders();

        assertEquals(1, responses.size());
        KitchenOrderDTOResponse response = responses.get(0);
        assertEquals(OrderStatus.PLACED, response.status());
        assertEquals(expectedPaymentStatus, response.paymentStatus());
    }

    private ProductEntity product(BigDecimal discount) {
        return new ProductEntity(2L, "Sushi", null, "Salmon sushi", "sushi.png",
                new BigDecimal("10.00"), discount, true, false, new ArrayList<>());
    }

    private OrderDTORequest onsiteRequest() {
        ProductEntity product = product(null);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        return new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, OrderChannel.ONSITE, PaymentMethod.CASH_ONSITE);
    }

    private TableEntity table(int tableNumber) {
        TableEntity table = new TableEntity();
        table.setTableNumber(tableNumber);
        table.setDeviceIdentifier("tablet-12");
        return table;
    }

    @Test
    void getActiveKitchenOrdersReturnsOrdersMappedToKitchenDTO() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PROCESSING);
        order.setChefNote("Extra spicy");
        order.setCreatedAt(LocalDateTime.now());
        ProductEntity product = product(null);
        OrderProductEntity op = OrderProductEntity.builder()
                .order(order).product(product).quantity(new BigDecimal("3")).build();
        order.setOrderProducts(List.of(op));

        when(orderRepository.findByStatusIn(
                List.of(OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.DELAYED)))
                .thenReturn(List.of(order));

        List<KitchenOrderDTOResponse> result = service.getActiveKitchenOrders();

        assertEquals(1, result.size());
        KitchenOrderDTOResponse dto = result.get(0);
        assertEquals(OrderStatus.PROCESSING, dto.status());
        assertEquals("Extra spicy", dto.chefNote());
        assertEquals(1, dto.items().size());
        assertEquals("Sushi", dto.items().get(0).productName());
        assertEquals(new BigDecimal("3"), dto.items().get(0).quantity());
    }

    @Test
    void getActiveKitchenOrdersRecentOrderIsNotDelayed() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PROCESSING);
        order.setCreatedAt(LocalDateTime.now());
        order.setOrderProducts(new ArrayList<>());
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(order));

        List<KitchenOrderDTOResponse> result = service.getActiveKitchenOrders();

        assertEquals(false, result.get(0).isDelayed());
    }

    @Test
    void getActiveKitchenOrdersOldOrderIsDelayed() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PROCESSING);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        order.setOrderProducts(new ArrayList<>());
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(order));

        List<KitchenOrderDTOResponse> result = service.getActiveKitchenOrders();

        assertEquals(true, result.get(0).isDelayed());
    }

    @Test
    void updateKitchenStatusValidTransitionUpdatesAndSaves() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PLACED);
        order.setCreatedAt(LocalDateTime.now());
        order.setOrderProducts(new ArrayList<>());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        KitchenOrderDTOResponse response = service.updateKitchenStatus(1L, OrderStatus.PROCESSING);

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals(OrderStatus.PROCESSING, response.status());
        verify(orderRepository).save(order);
    }

    @Test
    void updateKitchenStatusInvalidStatusThrowsBadRequest() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PLACED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateKitchenStatus(1L, OrderStatus.PAID));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void updateKitchenStatusDeliveredOrderThrowsConflict() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateKitchenStatus(1L, OrderStatus.READY));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void updateKitchenStatusOrderNotFoundThrowsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateKitchenStatus(99L, OrderStatus.READY));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getActiveKitchenOrdersReturnsEmptyListWhenNoActiveOrders() {
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of());

        List<KitchenOrderDTOResponse> result = service.getActiveKitchenOrders();

        assertEquals(0, result.size());
    }

    @Test
    void updateKitchenStatusRejectsNullStatus() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PLACED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateKitchenStatus(1L, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void updateKitchenStatusOntheWayOrderThrowsConflict() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.ONTHEWAY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateKitchenStatus(1L, OrderStatus.READY));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    void getKitchenMetricsReturnsCorrectCountsAndAverage() {
        OrderEntity processingOrder = new OrderEntity();
        processingOrder.setStatus(OrderStatus.PROCESSING);
        processingOrder.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        processingOrder.setOrderProducts(new ArrayList<>());

        OrderEntity delayedOrder = new OrderEntity();
        delayedOrder.setStatus(OrderStatus.PROCESSING);
        delayedOrder.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        delayedOrder.setOrderProducts(new ArrayList<>());

        when(orderRepository.findByStatusIn(
                List.of(OrderStatus.PLACED, OrderStatus.PROCESSING, OrderStatus.DELAYED)))
                .thenReturn(List.of(processingOrder, delayedOrder));
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());

        KitchenMetricsDTOResponse metrics = service.getKitchenMetrics();

        assertEquals(2, metrics.totalActiveOrders());
        assertEquals(2, metrics.processingCount());
        assertEquals(1, metrics.delayedCount());
        assertEquals(0, metrics.readyCount());
    }

    @Test
    void getKitchenMetricsReturnsZeroWhenNoActiveOrders() {
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());

        KitchenMetricsDTOResponse metrics = service.getKitchenMetrics();

        assertEquals(0, metrics.totalActiveOrders());
        assertEquals(0.0, metrics.averagePreparationMinutes());
        assertEquals(0, metrics.processingCount());
        assertEquals(0, metrics.delayedCount());
        assertEquals(0, metrics.readyCount());
    }

    @Test
    void getKitchenMetricsCountsReadyOrdersSeparately() {
        OrderEntity readyOrder = new OrderEntity();
        readyOrder.setStatus(OrderStatus.READY);

        when(orderRepository.findByStatusIn(any())).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of(readyOrder));

        KitchenMetricsDTOResponse metrics = service.getKitchenMetrics();

        assertEquals(1, metrics.readyCount());
        assertEquals(0, metrics.totalActiveOrders());
    }

    @Test
    void getKitchenMetricsCountsPlacedOrdersAsProcessing() {
        OrderEntity placedOrder = new OrderEntity();
        placedOrder.setStatus(OrderStatus.PLACED);
        placedOrder.setCreatedAt(LocalDateTime.now());
        placedOrder.setOrderProducts(new ArrayList<>());

        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(placedOrder));
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());

        KitchenMetricsDTOResponse metrics = service.getKitchenMetrics();

        assertEquals(1, metrics.processingCount());
        assertEquals(0, metrics.delayedCount());
    }

    @Test
    void getKitchenMetricsCalculatesAverageForSingleOrder() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PROCESSING);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        order.setOrderProducts(new ArrayList<>());

        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(order));
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());

        KitchenMetricsDTOResponse metrics = service.getKitchenMetrics();

        assertEquals(10.0, metrics.averagePreparationMinutes(), 0.5);
    }

    @Test
    void getKitchenMetricsHandlesMultipleReadyOrders() {
        OrderEntity readyOrder1 = new OrderEntity();
        readyOrder1.setStatus(OrderStatus.READY);
        OrderEntity readyOrder2 = new OrderEntity();
        readyOrder2.setStatus(OrderStatus.READY);

        when(orderRepository.findByStatusIn(any())).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.READY))
                .thenReturn(List.of(readyOrder1, readyOrder2));

        KitchenMetricsDTOResponse metrics = service.getKitchenMetrics();

        assertEquals(2, metrics.readyCount());
    }

    @ParameterizedTest
    @CsvSource({
            "ONSITE, ONLINE_CARD",
            "ONSITE, CASH_ON_DELIVERY",
            "ONLINE, CASH_ONSITE",
            "ONLINE, CARD_ONSITE"
    })
    void createOrderRejectsInvalidChannelPaymentMethodCombination(
            OrderChannel channel, PaymentMethod paymentMethod) {
        OrderDTORequest request = new OrderDTORequest(
                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                null, channel, paymentMethod);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder(request, "tablet-12", null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository, tableRepository, orderRepository);
    }

        @Test
    void getDeliveryMetricsReturnsCorrectCounts() {
        when(orderRepository.findByStatus(OrderStatus.READY))
                .thenReturn(List.of(new OrderEntity(), new OrderEntity()));
        when(orderRepository.findByStatus(OrderStatus.ONTHEWAY))
                .thenReturn(List.of(new OrderEntity()));

        OrderEntity delivered1 = new OrderEntity();
        delivered1.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        delivered1.setDeliveredAt(LocalDateTime.now().minusMinutes(10));

        OrderEntity delivered2 = new OrderEntity();
        delivered2.setCreatedAt(LocalDateTime.now().minusMinutes(50));
        delivered2.setDeliveredAt(LocalDateTime.now().minusMinutes(30));

        when(orderRepository.findByStatusAndDeliveredAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of(delivered1, delivered2));

        DeliveryMetricsDTOResponse metrics = service.getDeliveryMetrics();

        assertEquals(2, metrics.readyCount());
        assertEquals(1, metrics.inTransitCount());
        assertEquals(2, metrics.deliveredTodayCount());
        assertEquals(20.0, metrics.averageDeliveryMinutes(), 0.5);
    }

    @Test
    void getDeliveryMetricsReturnsZeroWhenNoOrders() {
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.ONTHEWAY)).thenReturn(List.of());
        when(orderRepository.findByStatusAndDeliveredAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        DeliveryMetricsDTOResponse metrics = service.getDeliveryMetrics();

        assertEquals(0, metrics.readyCount());
        assertEquals(0, metrics.inTransitCount());
        assertEquals(0, metrics.deliveredTodayCount());
        assertEquals(0.0, metrics.averageDeliveryMinutes());
    }

        @Test
    void getDeliveryMetricsCalculatesAverageForSingleDelivery() {
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.ONTHEWAY)).thenReturn(List.of());

        OrderEntity delivered = new OrderEntity();
        delivered.setCreatedAt(LocalDateTime.now().minusMinutes(25));
        delivered.setDeliveredAt(LocalDateTime.now());

        when(orderRepository.findByStatusAndDeliveredAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of(delivered));

        DeliveryMetricsDTOResponse metrics = service.getDeliveryMetrics();

        assertEquals(1, metrics.deliveredTodayCount());
        assertEquals(25.0, metrics.averageDeliveryMinutes(), 0.5);
    }

    @Test
    void getDeliveryMetricsCountsOnlyReadyOrders() {
        when(orderRepository.findByStatus(OrderStatus.READY))
                .thenReturn(List.of(new OrderEntity(), new OrderEntity(), new OrderEntity()));
        when(orderRepository.findByStatus(OrderStatus.ONTHEWAY)).thenReturn(List.of());
        when(orderRepository.findByStatusAndDeliveredAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        DeliveryMetricsDTOResponse metrics = service.getDeliveryMetrics();

        assertEquals(3, metrics.readyCount());
        assertEquals(0, metrics.inTransitCount());
        assertEquals(0, metrics.deliveredTodayCount());
    }

    @Test
    void getDeliveryMetricsCountsOnlyInTransitOrders() {
        when(orderRepository.findByStatus(OrderStatus.READY)).thenReturn(List.of());
        when(orderRepository.findByStatus(OrderStatus.ONTHEWAY))
                .thenReturn(List.of(new OrderEntity(), new OrderEntity()));
        when(orderRepository.findByStatusAndDeliveredAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        DeliveryMetricsDTOResponse metrics = service.getDeliveryMetrics();

        assertEquals(0, metrics.readyCount());
        assertEquals(2, metrics.inTransitCount());
        assertEquals(0, metrics.deliveredTodayCount());
    }

    @Test
    void getDeliveryMetricsQueriesFromStartOfToday() {
        when(orderRepository.findByStatus(any())).thenReturn(List.of());
        when(orderRepository.findByStatusAndDeliveredAtGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        service.getDeliveryMetrics();

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findByStatusAndDeliveredAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(OrderStatus.DELIVERED), sinceCaptor.capture());

        assertEquals(LocalDate.now().atStartOfDay(), sinceCaptor.getValue());
    }
    @Test
    void markAsDeliveredUpdatesStatusAndSetsDeliveredAt() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.ONTHEWAY);
        order.setPaymentMethod(PaymentMethod.CARD_ONSITE);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderDTOResponse response = service.markAsDelivered(1L, null);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(OrderStatus.DELIVERED, response.status());
        assertEquals(order.getDeliveredAt(), order.getDeliveredAt());
        verify(orderRepository).save(order);
    }

    @Test
    void markAsDeliveredRejectsOrderNotOnTheWay() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsDelivered(1L, null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void markAsDeliveredOrderNotFoundThrowsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsDelivered(99L, null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void markAsDeliveredRequiresCashConfirmationForCashOnDelivery() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.ONTHEWAY);
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsDelivered(1L, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void markAsDeliveredAcceptsCashOnDeliveryWithConfirmation() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.ONTHEWAY);
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        DeliveryConfirmationDTORequest request = new DeliveryConfirmationDTORequest(true);
        OrderDTOResponse response = service.markAsDelivered(1L, request);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(OrderStatus.DELIVERED, response.status());
        verify(orderRepository).save(order);
    }

    @Test
    void markAsDeliveredRejectsFalseCashCollectedForCashOnDelivery() {
        OrderEntity order = new OrderEntity();
        order.setStatus(OrderStatus.ONTHEWAY);
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        DeliveryConfirmationDTORequest request = new DeliveryConfirmationDTORequest(false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsDelivered(1L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    static java.util.stream.Stream<
        org.junit.jupiter.params.provider.Arguments> chefNoteCases() {

    return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(
                    "Sin wasabi", "Sin wasabi"),
            org.junit.jupiter.params.provider.Arguments.of(
                    null, null),
            org.junit.jupiter.params.provider.Arguments.of(
                    "", null),
            org.junit.jupiter.params.provider.Arguments.of(
                    "   ", null),
            org.junit.jupiter.params.provider.Arguments.of(
                    "<b>Sin wasabi</b>", "Sin wasabi"),
            org.junit.jupiter.params.provider.Arguments.of(
                    "<script>alert(1)</script>Sin wasabi", "Sin wasabi"),
            org.junit.jupiter.params.provider.Arguments.of(
                    "a".repeat(500), "a".repeat(500)));
}

@ParameterizedTest
@MethodSource("chefNoteCases")
void createOrderPreparesChefNote(String input, String expected) {
    when(productRepository.findById(2L))
            .thenReturn(Optional.of(product(null)));

    when(orderRepository.save(any(OrderEntity.class)))
            .thenAnswer(call -> call.getArgument(0));

    OrderDTORequest request = new OrderDTORequest(
            List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
            input,
            OrderChannel.ONLINE,
            PaymentMethod.ONLINE_CARD);

    OrderDTOResponse response = service.createOrder(request, null, REGISTERED_USER_ID);

    ArgumentCaptor<OrderEntity> captor =
            ArgumentCaptor.forClass(OrderEntity.class);

    verify(orderRepository).save(captor.capture());

    assertEquals(expected, captor.getValue().getChefNote());
    assertEquals(expected, response.chefNote());
}
@Test
void createOrderRejectsChefNoteLongerThan500Characters() {
    OrderDTORequest request = new OrderDTORequest(
            List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
            "a".repeat(501),
            OrderChannel.ONLINE,
            PaymentMethod.ONLINE_CARD);

    ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.createOrder(request, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

    verifyNoInteractions(
            orderRepository,
            productRepository,
            tableRepository,
            userRepository);
}

}
