package dev.team1.invoices.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesSummaryDTOResponse(
    String period,
    LocalDate startDate,
    LocalDate endDate,
    long invoiceCount,
    BigDecimal totalRevenue,
    BigDecimal onsiteRevenue,
    BigDecimal deliveryRevenue
) {}
