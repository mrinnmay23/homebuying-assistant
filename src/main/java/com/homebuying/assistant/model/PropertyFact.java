package com.homebuying.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name="property_fact")
public class PropertyFact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="document_id", nullable = false)
    public RagDocument document;

    public Integer page;            // where we saw it
    public Integer bedrooms;
    public Integer bathrooms;
    public Integer price;           // store as integer (e.g., 300000)
    public String  city;
    @Column(columnDefinition="TEXT")
    public String amenities;
    // comma separated / free text

    // PropertyFact.java


}
