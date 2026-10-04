package dev.team1.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import dev.team1.invoices.dtos.SalesSummaryDTOResponse;

public final class PDFExporter {
  private PDFExporter() {}

  public static byte[] exportSalesSummary(SalesSummaryDTOResponse summary) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);

      PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      List<String> lines = List.of(
          "Periodo: " + summary.period(),
          "Fechas: " + summary.startDate() + " a " + summary.endDate(),
          "Facturas pagadas: " + summary.invoiceCount(),
          "Ingresos totales: " + summary.totalRevenue() + " EUR",
          "Ingresos en sala: " + summary.onsiteRevenue() + " EUR",
          "Ingresos a domicilio: " + summary.deliveryRevenue() + " EUR");

      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(bold, 20);
        content.newLineAtOffset(50, 790);
        content.showText("Resumen de ventas");
        content.setFont(regular, 12);
        content.newLineAtOffset(0, -36);
        for (String line : lines) {
          content.showText(line);
          content.newLineAtOffset(0, -24);
        }
        content.endText();
      }
      document.save(output);
      return output.toByteArray();
    }
  }
}
