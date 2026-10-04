package dev.team1.io;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import dev.team1.invoices.dtos.SalesSummaryDTOResponse;

/** Builds the printable sales and billing summary template. */
public final class PDFExporter {
  private static final Color NAVY = new Color(25, 43, 64);
  private static final Color TEAL = new Color(22, 137, 132);
  private static final Color INK = new Color(48, 59, 71);
  private static final Color MUTED = new Color(111, 124, 137);
  private static final Color PALE = new Color(241, 245, 247);
  private static final Color RULE = new Color(221, 228, 232);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Madrid");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter GENERATED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private PDFExporter() {}

  public static byte[] exportSalesSummary(SalesSummaryDTOResponse summary) throws IOException {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

      try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
        drawHeader(canvas, regular, bold, summary);
        drawMetrics(canvas, regular, bold, summary);
        drawChannelBreakdown(canvas, regular, bold, summary);
        drawFooter(canvas, regular, summary);
      }

      document.save(output);
      return output.toByteArray();
    }
  }

  private static void drawHeader(PDPageContentStream canvas, PDType1Font regular,
      PDType1Font bold, SalesSummaryDTOResponse summary) throws IOException {
    fillRect(canvas, NAVY, 0, 668, PDRectangle.A4.getWidth(), 174);
    text(canvas, regular, 9, Color.WHITE, 48, 810, "RESTAURANTE  /  INFORME DE FACTURACION");
    text(canvas, bold, 25, Color.WHITE, 48, 765, "Resumen de ventas");
    text(canvas, regular, 11, new Color(210, 223, 231), 48, 739,
        "Metricas de facturacion del periodo seleccionado");

    fillRect(canvas, TEAL, 48, 690, 95, 25);
    text(canvas, bold, 10, Color.WHITE, 60, 698, periodLabel(summary.period()).toUpperCase(Locale.ROOT));
    text(canvas, regular, 11, Color.WHITE, 158, 697,
        DATE_FORMAT.format(summary.startDate()) + " - " + DATE_FORMAT.format(summary.endDate()));
    text(canvas, regular, 9, new Color(210, 223, 231), 48, 677,
        "Generado el " + GENERATED_FORMAT.format(LocalDateTime.now(BUSINESS_ZONE)) + " (hora peninsular)");
  }

  private static void drawMetrics(PDPageContentStream canvas, PDType1Font regular,
      PDType1Font bold, SalesSummaryDTOResponse summary) throws IOException {
    float cardY = 536;
    float cardHeight = 102;
    float cardWidth = 158;
    float gap = 13;
    float firstX = 48;
    BigDecimal average = summary.invoiceCount() == 0 ? BigDecimal.ZERO
        : summary.totalRevenue().divide(BigDecimal.valueOf(summary.invoiceCount()), 2, RoundingMode.HALF_UP);

    drawCard(canvas, regular, bold, firstX, cardY, cardWidth, cardHeight,
        "INGRESOS TOTALES", money(summary.totalRevenue()), "Facturacion del periodo", true);
    drawCard(canvas, regular, bold, firstX + cardWidth + gap, cardY, cardWidth, cardHeight,
        "FACTURAS PAGADAS", NumberFormatUtil.integer(summary.invoiceCount()), "Operaciones completadas", false);
    drawCard(canvas, regular, bold, firstX + (cardWidth + gap) * 2, cardY, cardWidth, cardHeight,
        "PROMEDIO POR FACTURA", money(average), "Importe medio pagado", false);
  }

  private static void drawCard(PDPageContentStream canvas, PDType1Font regular, PDType1Font bold,
      float x, float y, float width, float height, String label, String value, String caption,
      boolean highlight) throws IOException {
    fillRect(canvas, highlight ? new Color(232, 246, 244) : PALE, x, y, width, height);
    fillRect(canvas, highlight ? TEAL : RULE, x, y, 3, height);
    text(canvas, bold, 8, MUTED, x + 13, y + height - 21, label);
    text(canvas, bold, value.length() > 17 ? 15 : 19, highlight ? TEAL : NAVY,
        x + 13, y + 45, value);
    text(canvas, regular, 8, MUTED, x + 13, y + 15, caption);
  }

  private static void drawChannelBreakdown(PDPageContentStream canvas, PDType1Font regular,
      PDType1Font bold, SalesSummaryDTOResponse summary) throws IOException {
    text(canvas, bold, 14, NAVY, 48, 493, "Desglose por canal");
    text(canvas, regular, 9, MUTED, 48, 476, "Ingresos incluidos en la facturacion total del periodo");
    line(canvas, RULE, 48, 461, 547, 461);

    drawChannel(canvas, regular, bold, 48, 423, "Sala", summary.onsiteRevenue(), summary.totalRevenue(), TEAL);
    drawChannel(canvas, regular, bold, 48, 370, "Domicilio", summary.deliveryRevenue(), summary.totalRevenue(), NAVY);
  }

  private static void drawChannel(PDPageContentStream canvas, PDType1Font regular, PDType1Font bold,
      float x, float y, String label, BigDecimal amount, BigDecimal total, Color color) throws IOException {
    text(canvas, bold, 11, INK, x, y + 18, label);
    text(canvas, bold, 11, NAVY, 335, y + 18, money(amount));
    text(canvas, regular, 9, MUTED, 475, y + 18, percentage(amount, total));
    fillRect(canvas, PALE, x, y, 499, 7);
    float share = total.signum() <= 0 ? 0 : amount.divide(total, 4, RoundingMode.HALF_UP).floatValue();
    if (share > 0) fillRect(canvas, color, x, y, Math.min(499, 499 * share), 7);
  }

  private static void drawFooter(PDPageContentStream canvas, PDType1Font regular,
      SalesSummaryDTOResponse summary) throws IOException {
    line(canvas, RULE, 48, 82, 547, 82);
    text(canvas, regular, 8, MUTED, 48, 64,
        "Resumen de facturacion | Periodo: " + periodLabel(summary.period())
            + " | Datos expresados en EUR");
    text(canvas, regular, 8, MUTED, 48, 48,
        "Documento generado automaticamente para uso administrativo y contable.");
  }

  private static String periodLabel(String period) {
    return switch (period.toLowerCase(Locale.ROOT)) {
      case "dia", "day" -> "Dia";
      case "semana", "week" -> "Semana";
      case "mes", "month" -> "Mes";
      default -> period;
    };
  }

  private static String money(BigDecimal amount) {
    return NumberFormatUtil.decimal(amount) + " EUR";
  }

  private static String percentage(BigDecimal amount, BigDecimal total) {
    if (total.signum() <= 0) return "0,00 %";
    BigDecimal value = amount.multiply(BigDecimal.valueOf(100))
        .divide(total, 2, RoundingMode.HALF_UP);
    return NumberFormatUtil.decimal(value) + " %";
  }

  private static void text(PDPageContentStream canvas, PDType1Font font, float size,
      Color color, float x, float y, String value) throws IOException {
    canvas.beginText();
    canvas.setFont(font, size);
    canvas.setNonStrokingColor(color);
    canvas.newLineAtOffset(x, y);
    canvas.showText(value);
    canvas.endText();
  }

  private static void fillRect(PDPageContentStream canvas, Color color,
      float x, float y, float width, float height) throws IOException {
    canvas.setNonStrokingColor(color);
    canvas.addRect(x, y, width, height);
    canvas.fill();
  }

  private static void line(PDPageContentStream canvas, Color color,
      float x1, float y, float x2, float x2y) throws IOException {
    canvas.setStrokingColor(color);
    canvas.moveTo(x1, y);
    canvas.lineTo(x2, x2y);
    canvas.stroke();
  }

  private static final class NumberFormatUtil {
    private static String decimal(BigDecimal value) {
      return new DecimalFormat("#,##0.00",
          DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES"))).format(value);
    }

    private static String integer(long value) {
      return java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(value);
    }
  }
}
