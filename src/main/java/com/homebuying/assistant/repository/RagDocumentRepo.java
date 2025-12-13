package com.homebuying.assistant.repository;

import com.homebuying.assistant.model.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RagDocumentRepo extends JpaRepository<RagDocument, Long> {
    Optional<RagDocument> findByFilename(String filename);
}
