package dev.team1.invoices.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesChannelDistributionDTOResponse(
    LocalDate from,
    LocalDate to,
    BigDecimal total,
    List<SalesKpiDTOResponse.ChannelSales> channels
) {}
