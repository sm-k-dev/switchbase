package com.switchbase.ratings;

import jakarta.persistence.*;

/** 개조 태그. normalized가 같으면 같은 태그다(Q5). */
@Entity
@Table(name = "mod_tag")
public class ModTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "normalized", nullable = false, length = 50, unique = true)
    private String normalized;

    protected ModTag() {}

    ModTag(String name, String normalized) {
        this.name = name;
        this.normalized = normalized;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getNormalized() { return normalized; }
}
