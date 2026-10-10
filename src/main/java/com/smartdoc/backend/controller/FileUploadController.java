package com.smartdoc.backend.controller;

import com.smartdoc.backend.entity.Document;
import com.smartdoc.backend.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final DocumentService documentService;

    public FileUploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file.");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            return ResponseEntity.badRequest().body("Invalid file name.");
        }

        String fileName = Paths.get(originalName).getFileName().toString();
        Path savedPath;

        try {
            Path directory = Paths.get("uploads");
            Files.createDirectories(directory);

            savedPath = directory.resolve(UUID.randomUUID() + "_" + fileName);
            Files.copy(file.getInputStream(), savedPath);

            Document document = new Document();
            document.setFileName(fileName);
            document.setFileType(getFileType(fileName));
            document.setFilePath(savedPath.toString());
            document.setStatus("uploaded");

            return ResponseEntity.ok(documentService.saveDocument(document));

        } catch (IOException exception) {
            return ResponseEntity.internalServerError().body("File upload failed.");
        }
    }

    private String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return "UNKNOWN";
        }
        return fileName.substring(dotIndex + 1).toUpperCase();
    }
}
