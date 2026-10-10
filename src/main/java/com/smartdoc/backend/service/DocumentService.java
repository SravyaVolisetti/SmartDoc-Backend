
package com.smartdoc.backend.service;

import com.smartdoc.backend.entity.Document;
import com.smartdoc.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document saveDocument(Document document) {
        return documentRepository.save(document);
    }

    public List<Document> getAllDocuments(String ownerEmail) {
        return documentRepository.findAllByOwnerEmail(ownerEmail);
    }

    public Optional<Document> getDocumentById(
            String id,
            String ownerEmail) {

        return documentRepository.findByIdAndOwnerEmail(
                id,
                ownerEmail);
    }

    public void deleteDocument(String id, String ownerEmail) {
        documentRepository.deleteByIdAndOwnerEmail(
                id,
                ownerEmail);
    }
}
