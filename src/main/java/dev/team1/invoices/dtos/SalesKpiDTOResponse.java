package dev.team1.invoices.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.team1.enums.OrderChannel;

public record SalesKpiDTOResponse(
    Metric today,
    Metric month,
    Metric quarter,
    Metric fiscalYear,
    List<ChannelSales> channelSales,
    List<DailySales> weeklySales,
    LocalDate busiestDay
) {
  public record Metric(BigDecimal amount, BigDecimal variationPercent) {}
  public record ChannelSales(OrderChannel channel, BigDecimal amount, BigDecimal percentage) {}
  public record DailySales(LocalDate date, BigDecimal onsite, BigDecimal online, BigDecimal total) {}
}
