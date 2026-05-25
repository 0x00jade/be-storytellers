package com.demo.bestorytellers.reading.entity;

import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_lists")
public class ReadingList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultList = false;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected ReadingList() {}

    public ReadingList(User user, String name, boolean defaultList) {
        this.user = user;
        this.name = name;
        this.defaultList = defaultList;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public boolean isDefaultList() { return defaultList; }
    public Instant getCreatedAt() { return createdAt; }
    public void setName(String name) { this.name = name; }
}
