package com.demo.bestorytellers.auth.security;

import com.demo.bestorytellers.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserPrincipal implements OAuth2User, UserDetails {

    private final UUID userId;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> attributes;

    public UserPrincipal(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail());
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

    @Override public Map<String, Object> getAttributes() { return attributes != null ? attributes : Map.of(); }
    @Override public String getName() { return userId.toString(); }

    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}
