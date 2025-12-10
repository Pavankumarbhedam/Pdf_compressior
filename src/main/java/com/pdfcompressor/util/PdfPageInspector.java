package com.pdfcompressor.util;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Tiny helper to guess if a page is "image-heavy" (scanned) vs text.
 * We only use it to slightly adjust DPI/quality, nothing heavy.
 */
public class PdfPageInspector {

    public static boolean isImageHeavy(PDDocument doc, int pageIndex) {
        try {
            PDPage page = doc.getPage(pageIndex);
            var resources = page.getResources();
            if (resources == null) return false;

            for (COSName name : resources.getXObjectNames()) {
                try {
                    PDXObject obj = resources.getXObject(name);
                    if (obj instanceof PDImageXObject) {
                        return true; // has at least one embedded image
                    }
                } catch (Exception ignore) {
                }
            }
            return false;
        } catch (Exception e) {
            // On any error, just assume image heavy (safer DPI)
            return true;
        }
    }
}
