package com.demo.bestorytellers.common.util;

import java.text.Normalizer;
import java.util.UUID;

public final class SlugUtil {

    private SlugUtil() {}

    /**
     * Generates a URL-safe slug from a title and UUID.
     * Format: slugified-title-{first8charsOfUuid}
     */
    public static String generate(String title, UUID id) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "")
            .toLowerCase()
            .trim()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("[\\s-]+", "-")
            .replaceAll("^-|-$", "");

        String suffix = id.toString().replace("-", "").substring(0, 8);
        return normalized + "-" + suffix;
    }

    public static String tagSlug(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "")
            .toLowerCase()
            .trim()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("^-|-$", "");
    }
}
