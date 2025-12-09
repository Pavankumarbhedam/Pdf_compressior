package com.pdfcompressor.util;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class PdfPageInspector {

    public static boolean isImageHeavy(PDDocument doc, int pageIndex) {
        try {
            PDPage page = doc.getPage(pageIndex);

            for (COSName name : page.getResources().getXObjectNames()) {
                try {
                    PDXObject obj = page.getResources().getXObject(name);
                    if (obj instanceof PDImageXObject) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }

            return false;

        } catch (Exception e) {
            return true;
        }
    }
}
