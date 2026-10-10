package com.smartdoc.backend.controller;

import com.smartdoc.backend.service.ConversionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convert")
public class ConversionController {
    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    private String email(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Please log in.");
        }
        return authentication.getName().trim().toLowerCase(Locale.ROOT);
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String fileName, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @PostMapping("/txt-to-pdf/{id}")
    public ResponseEntity<byte[]> txtToPdf(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertTxtToPdf(id, email(auth)), "converted-document.pdf", "application/pdf");
    }

    @PostMapping("/pdf-to-txt/{id}")
    public ResponseEntity<byte[]> pdfToTxt(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertPdfToTxt(id, email(auth)), "converted-document.txt", "text/plain; charset=UTF-8");
    }

    @PostMapping("/image-to-pdf/{id}")
    public ResponseEntity<byte[]> imageToPdf(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertImageToPdf(id, email(auth)), "converted-document.pdf", "application/pdf");
    }

    @PostMapping("/image-format/{id}")
    public ResponseEntity<byte[]> imageFormat(@PathVariable String id, @RequestParam("format") String format, Authentication auth) throws IOException {
        String target = format.trim().toLowerCase(Locale.ROOT).replace("jpeg", "jpg");
        String mime = switch (target) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
        return fileResponse(conversionService.convertImageFormat(id, email(auth), format), "converted-image." + target, mime);
    }

    @PostMapping("/docx-to-pdf/{id}")
    public ResponseEntity<byte[]> docxToPdf(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertDocxToPdf(id, email(auth)), "converted-document.pdf", "application/pdf");
    }

    @PostMapping("/pdf-to-docx/{id}")
    public ResponseEntity<byte[]> pdfToDocx(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertPdfToDocx(id, email(auth)), "converted-document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @PostMapping("/docx-to-txt/{id}")
    public ResponseEntity<byte[]> docxToTxt(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertDocxToTxt(id, email(auth)), "converted-document.txt", "text/plain; charset=UTF-8");
    }

    @PostMapping("/txt-to-docx/{id}")
    public ResponseEntity<byte[]> txtToDocx(@PathVariable String id, Authentication auth) throws IOException {
        return fileResponse(conversionService.convertTxtToDocx(id, email(auth)), "converted-document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> ioError(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not convert this file.");
    }
}
