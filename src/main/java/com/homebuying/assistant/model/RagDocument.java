package com.homebuying.assistant.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="rag_document")
public class RagDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable=false, unique=true)
    public String filename;        // loan-estimate-1.pdf

    public Integer pages;          // optional

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RagChunk> chunks = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PropertyFact> facts = new ArrayList<>();
}
