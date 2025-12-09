package com.pdfcompressor.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class JpegEncoder {

    public static BufferedImage toGray(BufferedImage source) {

        BufferedImage rgb = new BufferedImage(
                source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = rgb.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();

        BufferedImage gray = new BufferedImage(
                source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        ColorConvertOp op = new ColorConvertOp(
                ColorSpace.getInstance(ColorSpace.CS_GRAY), null
        );

        op.filter(rgb, gray);

        return gray;
    }

    public static byte[] toJpeg(BufferedImage image, float quality) throws IOException {

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
