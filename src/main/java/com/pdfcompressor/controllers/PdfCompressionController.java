package com.pdfcompressor.controllers;

import com.pdfcompressor.service.PdfCompressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfCompressionController {
    @Autowired
    private  PdfCompressionService pdfService;
    private static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024; // 8 MB

    @PostMapping(
            value = "/compress",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "100") int targetKb
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file uploaded.");
            }

            if (file.getSize() > MAX_UPLOAD_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body("File too large. Max allowed is 8 MB on this server.");
            }

            byte[] output = pdfService.compressPdf(file, targetKb);

            String originalName = file.getOriginalFilename();
            if (originalName == null) {
                originalName = "document.pdf";
            }
            String downloadName = originalName.replaceAll("(?i)\\.pdf$", "") + "_compressed.pdf";

            System.out.println("========= OUTPUT FILE INFO =========");
            System.out.println("Original Name    : " + originalName);
            System.out.println("Compressed Size  : " + (output.length / 1024.0) + " KB");
            System.out.println("Saved As         : " + downloadName);
            System.out.println("====================================");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + downloadName + "\"")
                    .body(output);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Compression failed. Please try again later.");
        }
    }
}
