package dev.team1.io;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Creates a small, dependency-free PDF document for downloadable reports. */
public final class PDFExporter {
  private PDFExporter() {}

  public static byte[] export(String title, List<String> lines) {
    StringBuilder content = new StringBuilder("BT\n/F1 18 Tf\n50 780 Td\n(")
        .append(escape(title)).append(") Tj\n/F1 11 Tf\n");
    for (String line : lines) {
      content.append("0 -24 Td\n(").append(escape(line)).append(") Tj\n");
    }
    content.append("ET\n");
    byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);

    List<byte[]> objects = new ArrayList<>();
    objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
    objects.add(ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
    objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] "
        + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"));
    objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
    objects.add(concat(ascii("<< /Length " + stream.length + " >>\nstream\n"), stream, ascii("endstream")));

    byte[] header = new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', '%', (byte) 0xE2,
        (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'};
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(header);
    int offset = header.length;
    List<Integer> offsets = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      offsets.add(offset);
      byte[] object = concat(ascii((i + 1) + " 0 obj\n"), objects.get(i), ascii("\nendobj\n"));
      chunks.add(object);
      offset += object.length;
    }
    int xrefOffset = offset;
    StringBuilder xref = new StringBuilder("xref\n0 ").append(objects.size() + 1)
        .append("\n0000000000 65535 f \n");
    for (int objectOffset : offsets) {
      xref.append(String.format("%010d 00000 n \n", objectOffset));
    }
    xref.append("trailer\n<< /Size ").append(objects.size() + 1)
        .append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF");
    chunks.add(ascii(xref.toString()));
    return concat(chunks.toArray(byte[][]::new));
  }

  private static String escape(String value) {
    String asciiText = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").replaceAll("[^\\x20-\\x7E]", "?");
    return asciiText.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
  }

  private static byte[] ascii(String value) {
    return value.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] concat(byte[]... arrays) {
    int length = 0;
    for (byte[] array : arrays) length += array.length;
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }
}
