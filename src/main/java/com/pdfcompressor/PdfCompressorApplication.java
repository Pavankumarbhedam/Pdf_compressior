package com.pdfcompressor;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.imageio.ImageIO;

@SpringBootApplication
public class PdfCompressorApplication {
	public static void main(String[] args) {
		SpringApplication.run(PdfCompressorApplication.class, args);
	}

}
