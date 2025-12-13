package com.homebuying.assistant.repository;

import com.homebuying.assistant.model.RagChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RagChunkRepo extends JpaRepository<RagChunk, Long> {
    List<RagChunk> findByDocumentId(Long docId);
    List<RagChunk> findAll(); // we’ll scan all for retrieval
}
