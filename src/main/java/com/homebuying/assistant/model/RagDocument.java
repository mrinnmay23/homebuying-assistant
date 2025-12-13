package com.homebuying.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name="rag_document")
public class RagDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable=false, unique=true)
    public String filename;        // loan-estimate-1.pdf

    public Integer pages;          // optional
}
