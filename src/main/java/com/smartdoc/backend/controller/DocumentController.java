package com.smartdoc.backend.controller;

import com.smartdoc.backend.entity.Document;
import com.smartdoc.backend.service.DocumentService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final GridFsTemplate gridFsTemplate;

    private final Path uploadDirectory =
            Paths.get("uploads").toAbsolutePath().normalize();

    public DocumentController(
            DocumentService documentService,
            GridFsTemplate gridFsTemplate) {
        this.documentService = documentService;
        this.gridFsTemplate = gridFsTemplate;
    }

    private String currentEmail(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Please log in.");
        }

        return authentication.getName().trim().toLowerCase(Locale.ROOT);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select a file to upload.");
        }

        Path outputPath = null;
        String gridFsId = null;

        try {
            String ownerEmail = currentEmail(authentication);

            Files.createDirectories(uploadDirectory);

            String originalName = Paths.get(
                    file.getOriginalFilename() == null
                            ? "document"
                            : file.getOriginalFilename())
                    .getFileName()
                    .toString();

            if (originalName.isBlank()) {
                originalName = "document";
            }

            String storedName = new ObjectId() + "_" + originalName;
            outputPath = uploadDirectory.resolve(storedName).normalize();

            if (!outputPath.startsWith(uploadDirectory)) {
                return ResponseEntity.badRequest()
                        .body("Invalid file name.");
            }

            Files.copy(file.getInputStream(), outputPath);

            String extension = "";
            int dot = originalName.lastIndexOf('.');

            if (dot >= 0 && dot < originalName.length() - 1) {
                extension = originalName.substring(dot + 1)
                        .toUpperCase(Locale.ROOT);
            }

            String fileType = extension.isBlank()
                    ? "UNKNOWN"
                    : extension;

            String contentType = file.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : file.getContentType();

            try (var input = file.getInputStream()) {
                gridFsId = gridFsTemplate
                        .store(input, originalName, contentType)
                        .toString();
            }

            Document document = new Document();
            document.setFileName(originalName);
            document.setFileType(fileType);
            document.setFilePath(outputPath.toString());
            document.setStatus("Uploaded");
            document.setGridFsId(gridFsId);
            document.setOwnerEmail(ownerEmail);

            Document savedDocument = documentService.saveDocument(document);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(savedDocument);

        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Please log in again.");

        } catch (IOException | RuntimeException exception) {
            try {
                if (outputPath != null) {
                    Files.deleteIfExists(outputPath);
                }

                if (gridFsId != null && ObjectId.isValid(gridFsId)) {
                    gridFsTemplate.delete(
                            Query.query(
                                    Criteria.where("_id")
                                            .is(new ObjectId(gridFsId))));
                }
            } catch (Exception cleanupException) {
                // Preserve the original upload error.
            }

            exception.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not save the uploaded file.");
        }
    }

    @GetMapping
    public ResponseEntity<List<Document>> getAllDocuments(
            Authentication authentication) {

        String ownerEmail = currentEmail(authentication);

        return ResponseEntity.ok(
                documentService.getAllDocuments(ownerEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(
            @PathVariable String id,
            Authentication authentication) {

        String ownerEmail = currentEmail(authentication);

        return documentService
                .getDocumentById(id, ownerEmail)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable String id,
            Authentication authentication) {

        String ownerEmail = currentEmail(authentication);

        var optional = documentService.getDocumentById(id, ownerEmail);

        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document document = optional.get();

        try {
            if (document.getFilePath() == null
                    || document.getFilePath().isBlank()) {
                return ResponseEntity.notFound().build();
            }

            Path path = Paths.get(document.getFilePath())
                    .toAbsolutePath()
                    .normalize();

            if (!Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            byte[] bytes = Files.readAllBytes(path);

            String fileName = document.getFileName() == null
                    ? "document"
                    : document.getFileName();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(fileName)
                            .build());

            return new ResponseEntity<>(
                    bytes, headers, HttpStatus.OK);

        } catch (IOException exception) {
            exception.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not read the document.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String id,
            Authentication authentication) {

        String ownerEmail = currentEmail(authentication);

        var optional = documentService.getDocumentById(id, ownerEmail);

        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document document = optional.get();

        try {
            if (document.getFilePath() != null
                    && !document.getFilePath().isBlank()) {
                Path path = Paths.get(document.getFilePath())
                        .toAbsolutePath()
                        .normalize();

                if (Files.isRegularFile(path)) {
                    Files.deleteIfExists(path);
                }
            }

            if (document.getGridFsId() != null
                    && ObjectId.isValid(document.getGridFsId())) {
                gridFsTemplate.delete(
                        Query.query(
                                Criteria.where("_id")
                                        .is(new ObjectId(
                                                document.getGridFsId()))));
            }

            documentService.deleteDocument(id, ownerEmail);

            return ResponseEntity.noContent().build();

        } catch (IOException exception) {
            exception.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not delete the document file.");
        }
    }
}