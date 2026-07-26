package com.example.service;

import com.example.model.Loan;
import com.example.model.Transaction;
import com.example.model.User;

import jakarta.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@ApplicationScoped
public class StatementPdfService {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final SimpleDateFormat DATE_TIME = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
    private static final SimpleDateFormat DATE_ONLY = new SimpleDateFormat("dd-MMM-yyyy");

    public byte[] buildSavingsStatement(User member,
                                        String accountNumber,
                                        BigDecimal savingsBalance,
                                        BigDecimal availableForWithdrawal,
                                        List<Transaction> transactions,
                                        RunningBalanceResolver balanceResolver,
                                        String logoPath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Transaction tx : transactions) {
            rows.add(new String[]{
                    DATE_TIME.format(tx.getCreatedAt()),
                    String.valueOf(tx.getTransactionType()),
                    money(tx.getAmount()),
                    money(balanceResolver.resolve(tx))
            });
        }

        return new SimplePdf()
                .withLogo(logoPath)
                .withTitle("Savings Ledger Statement")
                .withMember(member, accountNumber)
                .withSummary(new String[][]{
                        {"Savings Balance", money(savingsBalance)},
                        {"Available for Withdrawal", money(availableForWithdrawal)}
                })
                .withTable(new String[]{"Date", "Transaction Type", "Amount", "Running Balance"}, rows)
                .build();
    }

    public byte[] buildLoanStatement(User member,
                                     String accountNumber,
                                     BigDecimal activeLoanBalance,
                                     BigDecimal maximumEligibility,
                                     List<Loan> loans,
                                     String logoPath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Loan loan : loans) {
            rows.add(new String[]{
                    loan.getAppliedAt() == null ? "N/A" : DATE_ONLY.format(loan.getAppliedAt()),
                    "Loan Application - " + loan.getStatus(),
                    money(loan.getPrincipalAmount()),
                    money(loan.getTotalRepayable().subtract(loan.getAmountRepaid()))
            });
        }

        return new SimplePdf()
                .withLogo(logoPath)
                .withTitle("Loan Ledger Statement")
                .withMember(member, accountNumber)
                .withSummary(new String[][]{
                        {"Active Loan Balance", money(activeLoanBalance)},
                        {"Maximum Eligibility", money(maximumEligibility)}
                })
                .withTable(new String[]{"Date", "Transaction Type", "Amount", "Running Balance"}, rows)
                .build();
    }

    private static String money(BigDecimal amount) {
        return "UGX " + MONEY.format(amount == null ? BigDecimal.ZERO : amount);
    }

    @FunctionalInterface
    public interface RunningBalanceResolver {
        BigDecimal resolve(Transaction transaction);
    }

    private static class SimplePdf {
        private String logoPath;
        private String title;
        private User member;
        private String accountNumber;
        private String[][] summary = new String[0][0];
        private String[] headers = new String[0];
        private List<String[]> rows = List.of();

        SimplePdf withLogo(String logoPath) {
            this.logoPath = logoPath;
            return this;
        }

        SimplePdf withTitle(String title) {
            this.title = title;
            return this;
        }

        SimplePdf withMember(User member, String accountNumber) {
            this.member = member;
            this.accountNumber = accountNumber;
            return this;
        }

        SimplePdf withSummary(String[][] summary) {
            this.summary = summary;
            return this;
        }

        SimplePdf withTable(String[] headers, List<String[]> rows) {
            this.headers = headers;
            this.rows = rows;
            return this;
        }

        byte[] build() throws IOException {
            PdfDocument pdf = new PdfDocument();
            int fontRegular = pdf.addRawObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
            int fontBold = pdf.addRawObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
            ImageResource logo = loadLogo();
            int logoObject = logo == null ? 0 : pdf.addStreamObject(
                    "<< /Type /XObject /Subtype /Image /Width " + logo.width + " /Height " + logo.height + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode >>",
                    logo.bytes
            );

            List<String[]> printableRows = rows.isEmpty()
                    ? Collections.singletonList(new String[]{"No records available", "", "", ""})
                    : rows;

            int rowIndex = 0;
            while (rowIndex < printableRows.size()) {
                PageRenderResult result = renderPage(rowIndex, printableRows, logoObject != 0);
                rowIndex = result.nextRowIndex;
                pdf.addPage(result.content, fontRegular, fontBold, logoObject);
            }

            return pdf.finish();
        }

        private PageRenderResult renderPage(int startRow, List<String[]> printableRows, boolean hasLogo) {
            StringBuilder c = new StringBuilder();
            int y = 782;

            if (hasLogo) {
                c.append("q 64 0 0 64 48 736 cm /Logo Do Q\n");
            }

            text(c, "F2", 16, 126, 770, "Kimwanyi SACCO");
            text(c, "F1", 9, 126, 754, "Member Account Statement");
            text(c, "F2", 14, 48, 706, title);

            y = 682;
            text(c, "F2", 10, 48, y, "Member Details");
            y -= 18;
            text(c, "F1", 9, 48, y, "Full Name: " + safe(member.getFullName()));
            text(c, "F1", 9, 310, y, "Account Ref: " + safe(accountNumber));
            y -= 15;
            text(c, "F1", 9, 48, y, "National ID: " + safe(member.getNationalId()));
            text(c, "F1", 9, 310, y, "Phone: " + safe(member.getPhoneNumber()));
            y -= 15;
            text(c, "F1", 9, 48, y, "Email: " + safe(member.getEmail()));
            text(c, "F1", 9, 310, y, "Generated: " + DATE_TIME.format(new Date()));

            y -= 30;
            text(c, "F2", 10, 48, y, "Statement Summary");
            y -= 18;
            for (String[] item : summary) {
                text(c, "F1", 9, 48, y, item[0] + ": " + item[1]);
                y -= 14;
            }

            y -= 10;
            drawRect(c, 48, y - 4, 500, 20, "0.97 0.98 0.97");
            int[] xs = {54, 164, 314, 424};
            for (int i = 0; i < headers.length; i++) {
                text(c, "F2", 8, xs[i], y + 2, headers[i]);
            }
            y -= 18;

            int row = startRow;
            while (row < printableRows.size() && y > 54) {
                String[] cells = printableRows.get(row);
                for (int i = 0; i < Math.min(cells.length, xs.length); i++) {
                    text(c, "F1", 8, xs[i], y, truncate(cells[i], i == 1 ? 26 : 20));
                }
                line(c, 48, y - 7, 548, y - 7, "0.93 0.94 0.95");
                y -= 17;
                row++;
            }

            text(c, "F1", 8, 48, 28, "Generated by Kimwanyi SACCO Member Portal");
            text(c, "F1", 8, 450, 28, "Page generated electronically");
            return new PageRenderResult(c.toString().getBytes(), row);
        }

        private ImageResource loadLogo() throws IOException {
            if (logoPath == null || logoPath.isBlank()) {
                return null;
            }
            File file = new File(logoPath);
            if (!file.exists()) {
                return null;
            }
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                return null;
            }
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", out);
            return new ImageResource(source.getWidth(), source.getHeight(), out.toByteArray());
        }

        private static void text(StringBuilder c, String font, int size, int x, int y, String value) {
            c.append("BT /").append(font).append(' ').append(size).append(" Tf ")
                    .append(x).append(' ').append(y).append(" Td (")
                    .append(escape(value)).append(") Tj ET\n");
        }

        private static void drawRect(StringBuilder c, int x, int y, int w, int h, String rgb) {
            c.append(rgb).append(" rg ").append(x).append(' ').append(y).append(' ').append(w).append(' ').append(h).append(" re f\n");
        }

        private static void line(StringBuilder c, int x1, int y1, int x2, int y2, String rgb) {
            c.append(rgb).append(" RG ").append(x1).append(' ').append(y1).append(" m ").append(x2).append(' ').append(y2).append(" l S\n");
        }

        private static String safe(String value) {
            return value == null || value.isBlank() ? "N/A" : value;
        }

        private static String truncate(String value, int limit) {
            String safe = safe(value);
            return safe.length() <= limit ? safe : safe.substring(0, limit - 3) + "...";
        }

        private static String escape(String value) {
            return safe(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }
    }

    private record ImageResource(int width, int height, byte[] bytes) {}
    private record PageRenderResult(byte[] content, int nextRowIndex) {}

    private static class PdfDocument {
        private final List<byte[]> objects = new ArrayList<>();
        private final List<Integer> pageObjects = new ArrayList<>();
        private int pagesObjectNumber;

        int addRawObject(String raw) {
            objects.add(raw.getBytes());
            return objects.size();
        }

        int addStreamObject(String dictionary, byte[] data) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write((dictionary.substring(0, dictionary.length() - 2) + " /Length " + data.length + " >>\nstream\n").getBytes());
                out.write(data);
                out.write("\nendstream".getBytes());
            } catch (IOException ignored) {
                // ByteArrayOutputStream does not throw for these writes.
            }
            objects.add(out.toByteArray());
            return objects.size();
        }

        void addPage(byte[] content, int fontRegular, int fontBold, int logoObject) {
            int contentObject = addStreamObject("<< >>", content);
            int pageObjectNumber = objects.size() + 1;
            String xObject = logoObject == 0 ? "" : " /XObject << /Logo " + logoObject + " 0 R >>";
            String page = "<< /Type /Page /Parent PAGES_REF /MediaBox [0 0 595 842] /Resources << /Font << /F1 "
                    + fontRegular + " 0 R /F2 " + fontBold + " 0 R >>" + xObject + " >> /Contents "
                    + contentObject + " 0 R >>";
            objects.add(page.getBytes());
            pageObjects.add(pageObjectNumber);
        }

        byte[] finish() throws IOException {
            pagesObjectNumber = objects.size() + 1;
            StringBuilder kids = new StringBuilder();
            for (Integer pageObject : pageObjects) {
                kids.append(pageObject).append(" 0 R ");
            }
            objects.add(("<< /Type /Pages /Kids [" + kids + "] /Count " + pageObjects.size() + " >>").getBytes());
            int catalogObject = objects.size() + 1;
            objects.add(("<< /Type /Catalog /Pages " + pagesObjectNumber + " 0 R >>").getBytes());

            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            pdf.write("%PDF-1.4\n".getBytes());
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(pdf.size());
                String object = new String(objects.get(i)).replace("PAGES_REF", pagesObjectNumber + " 0 R");
                pdf.write(((i + 1) + " 0 obj\n").getBytes());
                pdf.write(object.getBytes());
                pdf.write("\nendobj\n".getBytes());
            }

            int xref = pdf.size();
            pdf.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes());
            pdf.write("0000000000 65535 f \n".getBytes());
            for (Integer offset : offsets) {
                pdf.write(String.format("%010d 00000 n \n", offset).getBytes());
            }
            pdf.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root " + catalogObject + " 0 R >>\nstartxref\n" + xref + "\n%%EOF").getBytes());
            return pdf.toByteArray();
        }
    }
}
