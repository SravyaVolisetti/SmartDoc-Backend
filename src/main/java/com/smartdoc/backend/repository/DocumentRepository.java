
package com.smartdoc.backend.repository;

import com.smartdoc.backend.entity.Document;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository
        extends MongoRepository<Document, String> {

    List<Document> findAllByOwnerEmail(String ownerEmail);

    Optional<Document> findByIdAndOwnerEmail(
            String id,
            String ownerEmail);

    void deleteByIdAndOwnerEmail(
            String id,
            String ownerEmail);
}
