package com.demo.bestorytellers.auth.oauth2;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String email = (String) attrs.get("email");
        String name = (String) attrs.get("name");
        String avatarUrl = (String) attrs.get("picture");
        String providerId = (String) attrs.get("sub");

        User user = userRepository.findByEmail(email)
            .map(existing -> updateExisting(existing, name, avatarUrl))
            .orElseGet(() -> createNew(email, name, avatarUrl, providerId));

        UserPrincipal principal = UserPrincipal.from(user);
        principal.setAttributes(attrs);
        return principal;
    }

    private User createNew(String email, String name, String avatarUrl, String providerId) {
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
        if (baseUsername.isEmpty()) {
            baseUsername = "user";
        }
        String username = generateUniqueUsername(baseUsername);
        User user = new User(email, username, name, avatarUrl, "GOOGLE", providerId);
        return userRepository.save(user);
    }

    private User updateExisting(User user, String name, String avatarUrl) {
        if (user.getDisplayName() == null) {
            user.setDisplayName(name);
        }
        if (user.getAvatarUrl() == null ||
            (avatarUrl != null && user.getAvatarUrl().contains("googleusercontent.com"))) {
            user.setAvatarUrl(avatarUrl);
        }
        return userRepository.save(user);
    }

    private String generateUniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        String candidate;
        do {
            candidate = base + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        } while (userRepository.existsByUsername(candidate));
        return candidate;
    }
}
