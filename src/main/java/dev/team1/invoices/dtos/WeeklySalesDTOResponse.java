package dev.team1.invoices.dtos;

import java.time.LocalDate;
import java.util.List;

public record WeeklySalesDTOResponse(
    LocalDate weekStart,
    LocalDate weekEnd,
    LocalDate peakDay,
    List<SalesKpiDTOResponse.DailySales> days
) {}
