package com.pdfcompressor.service;

import com.pdfcompressor.util.JpegEncoder;
import com.pdfcompressor.util.PdfPageInspector;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfCompressionService {

    // thresholds for switching to fast single-pass mode
    private static final long LARGE_BYTES_THRESHOLD = 8L * 1024 * 1024; // 8 MB
    private static final int LARGE_PAGE_THRESHOLD   = 30;

    public byte[] compressPdf(MultipartFile file, int targetKb) throws Exception {

        byte[] inputBytes = file.getBytes();
        long originalBytes = inputBytes.length;
        long targetBytes = targetKb * 1024L;

        // Already below target -> just return
        if (originalBytes <= targetBytes) {
            return inputBytes;
        }

        try (PDDocument doc = PDDocument.load(inputBytes)) {
            int pageCount = doc.getNumberOfPages();
            System.out.println("Total Pages: " + pageCount);

            // Decide mode based on size/pages
            if (originalBytes >= LARGE_BYTES_THRESHOLD || pageCount >= LARGE_PAGE_THRESHOLD) {
                System.out.println("Using FAST SINGLE-PASS mode for large PDF");
                return compressSinglePass(doc, targetBytes, originalBytes);
            } else {
                System.out.println("Using MULTI-STEP mode for normal PDF");
                return compressMultiStep(doc, targetBytes);
            }
        }
    }

    // =========================
    // 1) Multi-step (for small/medium PDFs)
    // =========================
    private byte[] compressMultiStep(PDDocument doc, long targetBytes) throws Exception {

        PDFRenderer renderer = new PDFRenderer(doc);

        int[] dpiSteps      = {120, 90, 70, 50};
        float[] qualitySteps= {0.70f, 0.55f, 0.40f, 0.25f};
        double[] scaleSteps = {1.0, 0.95, 0.88, 0.85};
        boolean[] graySteps = {false, false, false, true};

        List<byte[]> results = new ArrayList<>();
        List<Long> sizes     = new ArrayList<>();

        for (int step = 0; step < dpiSteps.length; step++) {
            System.out.println("\n[Multi-step] Trying step " + step);

            int dpi         = dpiSteps[step];
            float quality   = qualitySteps[step];
            double scale    = scaleSteps[step];
            boolean grayscale = graySteps[step];

            try (PDDocument out = new PDDocument();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    PDPage page = processPage(doc, renderer, i, dpi, quality, grayscale, scale);
                    out.addPage(page);
                }

                out.save(baos);
                long size = baos.size();
                System.out.println("STEP " + step + " OUTPUT SIZE = " + (size / 1024.0) + " KB");

                // Early exit if we already fit under target
                if (size <= targetBytes) {
                    System.out.println("EARLY RETURN: STEP " + step + " fits target");
                    return baos.toByteArray();
                }

                results.add(baos.toByteArray());
                sizes.add(size);
            }
        }

        // Fallback: pick best candidate
        return pickBest(results, sizes, targetBytes);
    }

    // =========================
    // 2) Single-pass (for large PDFs)
    // =========================
    private byte[] compressSinglePass(PDDocument doc, long targetBytes, long originalBytes) throws Exception {

        PDFRenderer renderer = new PDFRenderer(doc);

        double ratio = (double) targetBytes / originalBytes;
        System.out.println("Single-pass mode, ratio = " + ratio);

        int dpi;
        float quality;
        double scale;
        boolean grayscale;

        if (ratio >= 0.60) { // mild compression
            dpi = 110;
            quality = 0.70f;
            scale = 1.0;
            grayscale = false;
        } else if (ratio >= 0.30) { // medium
            dpi = 90;
            quality = 0.55f;
            scale = 0.95;
            grayscale = false;
        } else if (ratio >= 0.15) { // strong
            dpi = 80;
            quality = 0.40f;
            scale = 0.90;
            grayscale = false;  // still keep color, just harder compression
        } else { // ultra
            dpi = 70;
            quality = 0.30f;
            scale = 0.85;
            grayscale = true;   // for very strong compression, allow grayscale
        }

        try (PDDocument out = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = processPage(doc, renderer, i, dpi, quality, grayscale, scale);
                out.addPage(page);
            }

            out.save(baos);
            long size = baos.size();
            System.out.println("SINGLE-PASS OUTPUT SIZE = " + (size / 1024.0) + " KB");

            return baos.toByteArray();
        }
    }

    // =========================
    // Common page processing
    // =========================
    private PDPage processPage(PDDocument doc,
                               PDFRenderer renderer,
                               int pageIndex,
                               int dpi,
                               float quality,
                               boolean grayscale,
                               double scale) throws Exception {

        boolean scanned = PdfPageInspector.isImageHeavy(doc, pageIndex);

        // Render page
        BufferedImage img = renderer.renderImageWithDPI(pageIndex, dpi);

        // Apply grayscale only when requested AND not scanned
        if (grayscale && !scanned) {
            img = JpegEncoder.toGray(img);
        }

        // Resize (avoid resizing scanned pages to preserve readability)
        if (scale < 1.0 && !scanned) {
            int w = (int) (img.getWidth() * scale);
            int h = (int) (img.getHeight() * scale);
            BufferedImage resized = new BufferedImage(w, h, img.getType());
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            img = resized;
        }

        // Slightly bump quality for scanned pages
        float adjustedQuality = scanned ? Math.min(quality + 0.1f, 1.0f) : quality;

        // JPEG encode
        byte[] jpeg = JpegEncoder.toJpeg(img, adjustedQuality);
        BufferedImage finalImg = ImageIO.read(new ByteArrayInputStream(jpeg));

        PDPage newPage = new PDPage(new PDRectangle(finalImg.getWidth(), finalImg.getHeight()));

        try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, newPage)) {
            var imgObj = JPEGFactory.createFromImage(doc, finalImg);
            cs.drawImage(imgObj, 0, 0);
        }

        return newPage;
    }

    // =========================
    // Fallback best-fit selector
    // =========================
    private byte[] pickBest(List<byte[]> results, List<Long> sizes, long targetBytes) {

        // 1) Best quality under target
        long maxValidSize = -1;
        int bestValidIndex = -1;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i) <= targetBytes && sizes.get(i) > maxValidSize) {
                maxValidSize = sizes.get(i);
                bestValidIndex = i;
            }
        }
        if (bestValidIndex != -1) {
            System.out.println("BEST FIT (<= target) = step " + bestValidIndex +
                    " (" + (sizes.get(bestValidIndex) / 1024.0) + " KB)");
            return results.get(bestValidIndex);
        }

        // 2) Allow up to +10% margin
        long tenPercent = (long) (targetBytes * 1.1);
        long minAbove = Long.MAX_VALUE;
        int bestAboveIndex = -1;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i) <= tenPercent && sizes.get(i) < minAbove) {
                minAbove = sizes.get(i);
                bestAboveIndex = i;
            }
        }
        if (bestAboveIndex != -1) {
            System.out.println("BEST FIT (10% allowed) = step " + bestAboveIndex +
                    " (" + (sizes.get(bestAboveIndex) / 1024.0) + " KB)");
            return results.get(bestAboveIndex);
        }

        // 3) Fallback smallest
        long smallest = Long.MAX_VALUE;
        int smallestIndex = 0;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i) < smallest) {
                smallest = sizes.get(i);
                smallestIndex = i;
            }
        }
        System.out.println("BEST FIT (fallback smallest) = step " + smallestIndex +
                " (" + (sizes.get(smallestIndex) / 1024.0) + " KB)");
        return results.get(smallestIndex);
    }
}
