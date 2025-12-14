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
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
@Service
public class PdfCompressionService {

    private static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024; // 8 MB

    public byte[] compressPdf(MultipartFile file, int targetKb) throws Exception {

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Max allowed file size is 8 MB.");
        }

        byte[] inputBytes = file.getBytes();
        long originalBytes = inputBytes.length;
        long targetBytes = Math.max(targetKb, 20) * 1024L;

        if (originalBytes <= targetBytes) {
            return inputBytes;
        }

        try (PDDocument src = PDDocument.load(
                new ByteArrayInputStream(inputBytes),
                MemoryUsageSetting.setupTempFileOnly())) {

            boolean textPdf = isTextPdf(src);
            byte[] output;

            if (textPdf) {
                output = compressTextPdf(src);
            } else {
                output = compressScannedPdf(src);
            }

            // Never return larger file
            if (output.length >= originalBytes) {
                return inputBytes;
            }
            return output;
        }
    }

    /* ================= TEXT PDF COMPRESSION ================= */

    private byte[] compressTextPdf(PDDocument doc) throws Exception {

        doc.setAllSecurityToBeRemoved(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos); // PDFBox 2.x compatible
        return baos.toByteArray();
    }

    /* ================= SCANNED PDF COMPRESSION ================= */

    private byte[] compressScannedPdf(PDDocument src) throws Exception {

        PDFRenderer renderer = new PDFRenderer(src);

        try (PDDocument out = new PDDocument()) {

            int dpi = 90;
            float quality = 0.65f;

            for (int i = 0; i < src.getNumberOfPages(); i++) {

                BufferedImage image = renderer.renderImageWithDPI(i, dpi);

                byte[] jpegBytes = JpegEncoder.toJpeg(image, quality);
                BufferedImage finalImg = ImageIO.read(new ByteArrayInputStream(jpegBytes));

                PDPage page = new PDPage(
                        new PDRectangle(finalImg.getWidth(), finalImg.getHeight()));
                out.addPage(page);

                PDImageXObject imgObj = JPEGFactory.createFromImage(out, finalImg);

                try (PDPageContentStream cs =
                             new PDPageContentStream(out, page)) {

                    cs.drawImage(
                            imgObj,
                            0,
                            0,
                            finalImg.getWidth(),
                            finalImg.getHeight()
                    );
                }

                image.flush();
                finalImg.flush();
                System.gc();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out.save(baos);
            return baos.toByteArray();
        }
    }

    /* ================= PDF TYPE DETECTION ================= */

    private boolean isTextPdf(PDDocument doc) {
        try {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (PdfPageInspector.isImageHeavy(doc, i)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
