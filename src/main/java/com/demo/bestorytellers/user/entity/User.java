package com.demo.bestorytellers.user.entity;

import com.demo.bestorytellers.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider = "GOOGLE";

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected User() {}

    public User(String email, String username, String displayName, String avatarUrl,
                String provider, String providerId) {
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.provider = provider;
        this.providerId = providerId;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getBio() { return bio; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public boolean isActive() { return active; }

    public void setUsername(String username) { this.username = username; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setBio(String bio) { this.bio = bio; }
    public void setActive(boolean active) { this.active = active; }
}
