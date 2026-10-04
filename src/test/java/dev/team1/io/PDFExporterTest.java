package dev.team1.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import dev.team1.invoices.dtos.SalesSummaryDTOResponse;

class PDFExporterTest {

  @Test
  void exportSalesSummary_shouldGenerateReadablePdfForDayWeekAndMonth() throws Exception {
    Map<String, String> periods = Map.of(
        "dia", "DIA",
        "semana", "SEMANA",
        "mes", "MES");

    for (Map.Entry<String, String> period : periods.entrySet()) {
      SalesSummaryDTOResponse summary = new SalesSummaryDTOResponse(
          period.getKey(),
          LocalDate.of(2026, 10, 1),
          LocalDate.of(2026, 10, 4),
          3,
          new BigDecimal("90.00"),
          new BigDecimal("60.00"),
          new BigDecimal("30.00"));

      byte[] pdf = PDFExporter.exportSalesSummary(summary);

      assertTrue(pdf.length > 0, "PDF should contain bytes for period " + period.getKey());
      try (var document = Loader.loadPDF(pdf)) {
        String text = new PDFTextStripper().getText(document);
        assertTrue(text.contains("Resumen de ventas"));
        assertTrue(text.contains(period.getValue()), "PDF should show period " + period.getKey());
        assertTrue(text.contains("01/10/2026 - 04/10/2026"));
        assertTrue(text.contains("90,00 EUR"));
        assertTrue(text.contains("3"));
      }
    }
  }
}
