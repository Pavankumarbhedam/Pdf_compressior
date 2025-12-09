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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class PdfCompressionService {

    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public byte[] compressPdf(MultipartFile file, int targetKb) throws Exception {
        byte[] inputBytes = file.getBytes();
        long originalBytes = inputBytes.length;
        long targetBytes = targetKb * 1024L;

        // Already small enough
        if (originalBytes <= targetBytes) {
            return inputBytes;
        }

        try (PDDocument doc = PDDocument.load(inputBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);

            // Compression steps (FROM mild → ultra)
            int[] dpiSteps = {120, 90, 70, 50};
            float[] qualitySteps = {0.70f, 0.55f, 0.40f, 0.25f};
            double[] scaleSteps = {1.0, 0.95, 0.88, 0.85};
            boolean[] graySteps = {false, false, false, true};

            List<byte[]> results = new ArrayList<>();
            List<Long> sizes = new ArrayList<>();

            System.out.println("Total Pages: " + doc.getNumberOfPages());

            // Try compression levels sequentially with early stopping
            for (int step = 0; step < dpiSteps.length; step++) {
                System.out.println("\nTrying step " + step);

                int dpi = dpiSteps[step];
                float quality = qualitySteps[step];
                double scale = scaleSteps[step];
                boolean grayscale = graySteps[step];

                try (PDDocument out = new PDDocument();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    // Process pages in parallel for speed
                    List<CompletableFuture<PDPage>> pageFutures = new ArrayList<>();
                    for (int i = 0; i < doc.getNumberOfPages(); i++) {
                        int pageIndex = i;
                        CompletableFuture<PDPage> future = CompletableFuture.supplyAsync(() ->
                        {
                            try {
                                return processStep(doc, renderer, pageIndex, dpi, quality, grayscale, scale);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, executor);
                        pageFutures.add(future);
                    }

                    // Collect processed pages
                    for (CompletableFuture<PDPage> future : pageFutures) {
                        out.addPage(future.get());
                    }

                    out.save(baos);
                    long size = baos.size();
                    System.out.println("STEP " + step + " OUTPUT SIZE = " + (size / 1024.0) + " KB");

                    // Early return if under target (best quality)
                    if (size <= targetBytes) {
                        System.out.println("EARLY RETURN: STEP " + step + " fits target (" + (size / 1024.0) + " KB)");
                        return baos.toByteArray();
                    }

                    // Store for fallback selection
                    results.add(baos.toByteArray());
                    sizes.add(size);
                }
            }

            // Fallback: Choose the best from remaining results
            return pickBest(results, sizes, targetBytes);
        }
    }

    private PDPage processStep(PDDocument doc, PDFRenderer renderer, int pageIndex,
                               int dpi, float quality, boolean grayscale, double scale) throws Exception {
        boolean scanned = PdfPageInspector.isImageHeavy(doc, pageIndex);

        // Render at DPI
        BufferedImage img = renderer.renderImageWithDPI(pageIndex, dpi);

        // For scanned pages, preserve color in early steps; only grayscale in ultra
        if (grayscale && !scanned) {
            img = JpegEncoder.toGray(img);
        }

        // Resize if required (skip for scanned to avoid quality loss)
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

        // Adjust quality slightly higher for scanned pages to maintain accuracy
        float adjustedQuality = scanned ? Math.min(quality + 0.1f, 1.0f) : quality;

        // Convert to JPEG
        byte[] jpeg = JpegEncoder.toJpeg(img, adjustedQuality);
        BufferedImage finalImg = ImageIO.read(new ByteArrayInputStream(jpeg));

        PDPage newPage = new PDPage(new PDRectangle(finalImg.getWidth(), finalImg.getHeight()));
        try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, newPage)) {
            var imgObj = JPEGFactory.createFromImage(doc, finalImg);
            cs.drawImage(imgObj, 0, 0);
        }

        return newPage;
    }

    // FINAL SELECTION LOGIC (fallback only)
    private byte[] pickBest(List<byte[]> results, List<Long> sizes, long targetBytes) {
        // 1st priority → best quality under target (but we already returned early if any)
        // Since we're here, none were under; proceed to allowances

        // 2nd → Allow up to 10% deviation
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
            System.out.println("BEST FIT (10% allowed) = STEP " + bestAboveIndex +
                    " (" + (sizes.get(bestAboveIndex) / 1024.0) + " KB)");
            return results.get(bestAboveIndex);
        }

        // 3rd → Return smallest (last fallback)
        long smallest = Long.MAX_VALUE;
        int smallestIndex = 0;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i) < smallest) {
                smallest = sizes.get(i);
                smallestIndex = i;
            }
        }
        System.out.println("BEST FIT (fallback smallest) = STEP " + smallestIndex +
                " (" + (sizes.get(smallestIndex) / 1024.0) + " KB)");
        return results.get(smallestIndex);
    }
}
