package dev.team1.orders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import dev.team1.enums.OrderChannel;
import dev.team1.enums.OrderStatus;
import dev.team1.enums.PaymentMethod;
import dev.team1.enums.PaymentStatus;
import dev.team1.orders.dtos.OrderDTORequest;
import dev.team1.orders.dtos.OrderDTOResponse;
import dev.team1.security.JwtFilter;
import dev.team1.security.SecurityConfiguration;
import dev.team1.auth.CustomUserDetails;
import dev.team1.users.UserEntity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;


@WebMvcTest(controllers = OrderController.class, properties = "api-endpoint=api/v1")
@Import(SecurityConfiguration.class)
class OrderControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        JwtFilter jwtFilter;

        @MockitoBean
        private OrderService service;

        @BeforeEach
        void setup() throws Exception {
                doAnswer(invocation -> {
                        ServletRequest req = invocation.getArgument(0);
                        ServletResponse res = invocation.getArgument(1);
                        FilterChain chain = invocation.getArgument(2);
                        chain.doFilter(req, res);
                        return null;
                }).when(jwtFilter).doFilter(any(), any(), any());
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOrderReturnsCreatedOrder() throws Exception {
                UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 2)),
                                "No onions", OrderChannel.SALA, PaymentMethod.CARD_ONSITE);
                when(service.createOrder(request, "tablet-12", userId)).thenReturn(response(OrderStatus.PLACED));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(customer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Device-Identifier", "tablet-12")
                        .content("""
                                {"items":[{"productId":2,"quantity":2}],"chefNote":"No onions","channel":"SALA","paymentMethod":"CARD_ONSITE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PLACED"))
                                .andExpect(jsonPath("$.channel").value("SALA"))
                                .andExpect(jsonPath("$.paymentMethod").value("CARD_ONSITE"))
                                .andExpect(jsonPath("$.paymentStatus").value("PENDING_CARD_TERMINAL"))
                                .andExpect(jsonPath("$.tableNumber").value(12))
                                .andExpect(jsonPath("$.total").value(22.0));
                verify(service).createOrder(request, "tablet-12", userId);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOrderPassesDeviceIdentifierToService() throws Exception {
                UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                                null, OrderChannel.SALA, PaymentMethod.CASH_ONSITE);
                when(service.createOrder(request, "tablet-7", userId)).thenReturn(response(OrderStatus.PLACED));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(customer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Device-Identifier", "tablet-7")
                        .content("""
                                {"items":[{"productId":2,"quantity":1}],"channel":"ONSITE","paymentMethod":"CASH_ONSITE"}
                                """))
                .andExpect(status().isCreated());

                verify(service).createOrder(request, "tablet-7", userId);
        }

        @Test
        void createOnlineOrderPassesGuestRequestToService() throws Exception {
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                                null, OrderChannel.DOMICILIO, PaymentMethod.ONLINE_CARD);
                when(service.createOrder(request, null, null)).thenReturn(response(OrderStatus.PLACED));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":2,"quantity":1}],"channel":"ONLINE","paymentMethod":"ONLINE_CARD"}
                                """))
                .andExpect(status().isCreated());
                verify(service).createOrder(request, null, null);
        }

        @Test
        void createOrderPassesAuthenticatedUserIdToService() throws Exception {
                UUID userId = UUID.randomUUID();
                UserEntity customer = new UserEntity();
                customer.setId(userId);
                customer.setEmail("customer@example.com");
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                                null, OrderChannel.DOMICILIO, PaymentMethod.ONLINE_CARD);
                when(service.createOrder(request, null, userId)).thenReturn(response(OrderStatus.PLACED));

                mockMvc.perform(post("/api/v1/orders")
                                .with(user(new CustomUserDetails(customer)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"items":[{"productId":2,"quantity":1}],"channel":"ONLINE","paymentMethod":"ONLINE_CARD"}
                                        """))
                        .andExpect(status().isCreated());

                verify(service).createOrder(request, null, userId);
        }

        @Test
        void createOnsiteOrderPassesGuestRequestToService() throws Exception {
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                                null, OrderChannel.SALA, PaymentMethod.CASH_ONSITE);
                when(service.createOrder(request, "tablet-12", null)).thenReturn(response(OrderStatus.PLACED));

                mockMvc.perform(post("/api/v1/orders")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Device-Identifier", "tablet-12")
                                .content("""
                                        {"items":[{"productId":2,"quantity":1}],"channel":"ONSITE","paymentMethod":"CASH_ONSITE"}
                                        """))
                        .andExpect(status().isCreated());
                verify(service).createOrder(request, "tablet-12", null);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOnsiteOrderReturnsBadRequestWhenDeviceIdentifierIsMissing() throws Exception {
                UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                                null, OrderChannel.SALA, PaymentMethod.CASH_ONSITE);
                when(service.createOrder(request, null, userId)).thenThrow(new ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST, "Device identifier is required"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(customer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":2,"quantity":1}],"channel":"ONSITE","paymentMethod":"CASH_ONSITE"}
                                """))
                .andExpect(status().isBadRequest());

                verify(service).createOrder(request, null, userId);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOnsiteOrderReturnsNotFoundWhenDeviceIsUnknown() throws Exception {
                UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
                OrderDTORequest request = new OrderDTORequest(
                                List.of(new OrderDTORequest.OrderItemDTORequest(2L, 1)),
                                null, OrderChannel.SALA, PaymentMethod.CASH_ONSITE);
                when(service.createOrder(request, "unknown-device", userId)).thenThrow(new ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "No table found for the given device."));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .with(customer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Device-Identifier", "unknown-device")
                        .content("""
                                {"items":[{"productId":2,"quantity":1}],"channel":"ONSITE","paymentMethod":"CASH_ONSITE"}
                                """))
                .andExpect(status().isNotFound());

                verify(service).createOrder(request, "unknown-device", userId);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOrderRejectsMissingChannel() throws Exception {
                mockMvc.perform(post("/api/v1/orders")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"items":[{"productId":2,"quantity":2}],"paymentMethod":"CASH_ONSITE"}
                                        """))
                                .andExpect(status().isBadRequest());
                verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOrderRejectsMissingPaymentMethod() throws Exception {
                mockMvc.perform(post("/api/v1/orders")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"items":[{"productId":2,"quantity":2}],"channel":"ONSITE"}
                                        """))
                                .andExpect(status().isBadRequest());
                verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void createOrderRejectsInvalidEnumValue() throws Exception {
                mockMvc.perform(post("/api/v1/orders")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"items":[{"productId":2,"quantity":2}],"channel":"RESTAURANT","paymentMethod":"CASH_ONSITE"}
                                                """))
                                .andExpect(status().isBadRequest());
                verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(roles = "COOK")
        void markAsPaidReturnsPaidOrder() throws Exception {
                when(service.markAsPaid(1L)).thenReturn(response(OrderStatus.PAID));

        mockMvc.perform(patch("/api/v1/orders/1/paid")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAID"));
        verify(service).markAsPaid(1L);
    }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void getByIdReturnsOrderStatus() throws Exception {
                when(service.getById(1L)).thenReturn(response(OrderStatus.PAID));

                mockMvc.perform(get("/api/v1/orders/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.status").value("PAID"));
                verify(service).getById(1L);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void getByStatusReturnsPaidOrders() throws Exception {
                when(service.getByStatus(OrderStatus.PAID))
                                .thenReturn(List.of(response(OrderStatus.PAID)));

                mockMvc.perform(get("/api/v1/orders").param("status", "PAID"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].id").value(1))
                                .andExpect(jsonPath("$[0].status").value("PAID"));
                verify(service).getByStatus(OrderStatus.PAID);
        }

        private RequestPostProcessor customer(UUID userId) {
                UserEntity customer = new UserEntity();
                customer.setId(userId);
                return user(new CustomUserDetails(customer));
        }

        private OrderDTOResponse response(OrderStatus orderStatus) {
                PaymentStatus paymentStatus = null;

                if (orderStatus != OrderStatus.PAID) {
                        paymentStatus = PaymentStatus.PENDING_CARD_TERMINAL;
                }

                return new OrderDTOResponse(
                                1L,
                                new BigDecimal("20.00"),
                                null,
                                new BigDecimal("0.00"),
                                10,
                                new BigDecimal("22.00"),
                                new BigDecimal("2.00"),
                                "No onions",
                                orderStatus,
                                OrderChannel.SALA,
                                PaymentMethod.CARD_ONSITE,
                                12,
                                paymentStatus);
        }
        @Test
        @WithMockUser(roles = "CUSTOMER")
    void createOrderRejectsChefNoteLongerThan500Characters() throws Exception {
    String requestBody = """
            {
                "items": [{"productId": 2, "quantity": 1}],
                "chefNote": "%s",
                "channel": "ONLINE",
                "paymentMethod": "ONLINE_CARD"
            }
            """.formatted("a".repeat(501));

    mockMvc.perform(post("/api/v1/orders")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
}
}
