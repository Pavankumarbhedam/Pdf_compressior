package com.pdfcompressor.service;

import com.pdfcompressor.util.JpegEncoder;
import com.pdfcompressor.util.PdfPageInspector;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class PdfCompressionService {

    // Hard limit for free Render tier (512 MB)
    private static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024; // 8 MB

    /**
     * Compress a PDF file to roughly match the target size, with a RAM-safe pipeline.
     */
    public byte[] compressPdf(MultipartFile file, int targetKb) throws Exception {

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File too large. Max allowed is 8 MB on this server.");
        }

        byte[] inputBytes = file.getBytes();
        long originalBytes = inputBytes.length;
        long targetBytes = Math.max(targetKb, 20) * 1024L; // guard minimum 20 KB

        // If already small enough -> just return as-is
        if (originalBytes <= targetBytes) {
            return inputBytes;
        }

        // Decide compression strength from ratio
        double ratio = (double) targetBytes / (double) originalBytes;

        int baseDpi;
        float baseQuality;

        if (ratio >= 0.75) {
            // light compression
            baseDpi = 120;
            baseQuality = 0.80f;
        } else if (ratio >= 0.50) {
            baseDpi = 100;
            baseQuality = 0.70f;
        } else if (ratio >= 0.30) {
            baseDpi = 90;
            baseQuality = 0.60f;
        } else {
            // very strong compression
            baseDpi = 72;
            baseQuality = 0.50f;
        }

        // Use temp files instead of heap only -> huge memory win
        try (ByteArrayInputStream in = new ByteArrayInputStream(inputBytes);
             PDDocument src = PDDocument.load(in, MemoryUsageSetting.setupTempFileOnly());
             PDDocument out = new PDDocument()) {

            PDFRenderer renderer = new PDFRenderer(src);

            int pageCount = src.getNumberOfPages();
            System.out.println("Pages: " + pageCount + ", ratio=" + ratio +
                    " baseDpi=" + baseDpi + ", baseQ=" + baseQuality);

            for (int i = 0; i < pageCount; i++) {
                boolean scanned = PdfPageInspector.isImageHeavy(src, i);

                int dpi = scanned ? baseDpi - 10 : baseDpi;
                if (dpi < 60) dpi = 60;

                float quality = scanned ? Math.min(baseQuality + 0.05f, 0.85f) : baseQuality;

                // Render single page
                BufferedImage pageImage = renderer.renderImageWithDPI(i, dpi);

                // Compress to JPEG
                byte[] jpegBytes = JpegEncoder.toJpeg(pageImage, quality);

                // Read compressed JPEG back
                BufferedImage finalImg;
                try (ByteArrayInputStream imgIn = new ByteArrayInputStream(jpegBytes)) {
                    finalImg = ImageIO.read(imgIn);
                }

                // Create new page sized to image
                PDPage newPage = new PDPage(new PDRectangle(finalImg.getWidth(), finalImg.getHeight()));
                out.addPage(newPage);

                // Draw image on page
                PDImageXObject pdImage = JPEGFactory.createFromImage(out, finalImg);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(out, newPage)) {
                    cs.drawImage(pdImage, 0, 0,
                            finalImg.getWidth(), finalImg.getHeight());
                }

                // Help GC
                pageImage.flush();
                finalImg.flush();
                System.gc();
            }

            // Save result
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                out.save(baos);
                long outBytes = baos.size();
                System.out.println("Compressed size = " + (outBytes / 1024.0) + " KB");
                return baos.toByteArray();
            }
        }
    }
}
