package com.pdfcompressor.controllers;

import com.pdfcompressor.service.PdfCompressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfCompressionController {
   @Autowired
    private PdfCompressionService pdfService;
    @PostMapping(
            value = "/compress",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<byte[]> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "100") int targetKb
    ) {
        try {

            System.out.println("========= INPUT FILE INFO =========");
            System.out.println("File Name       : " + file.getOriginalFilename());
            System.out.println("File Size (KB)  : " + (file.getSize() / 1024.0) + " KB");
            System.out.println("Target Size (KB): " + targetKb);
            System.out.println("====================================");

            byte[] output = pdfService.compressPdf(file, targetKb);

            System.out.println("========= OUTPUT FILE INFO =========");
            System.out.println("Original Name    : " + file.getOriginalFilename());
            System.out.println("Compressed Size  : " + (output.length / 1024.0) + " KB");
            System.out.println("Saved As         :  "+file.getOriginalFilename()+"_compressed.pdf");
            System.out.println("====================================");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(output);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
