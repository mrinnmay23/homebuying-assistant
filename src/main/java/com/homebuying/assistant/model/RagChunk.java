package com.homebuying.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name="rag_chunk", indexes = {
        @Index(name="idx_doc", columnList="document_id")
})
public class RagChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional=false)
    @JoinColumn(name="document_id")
    public RagDocument document;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    public String text;          // chunk text

    @Lob
    @Column(name="embedding_json", columnDefinition = "LONGTEXT")
    public String embeddingJson; // store vector as JSON string (double[])
}
