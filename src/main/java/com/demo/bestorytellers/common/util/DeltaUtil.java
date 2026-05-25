package com.demo.bestorytellers.common.util;

import com.demo.bestorytellers.common.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DeltaUtil {

    private final ObjectMapper objectMapper;

    public DeltaUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates Quill Delta structure, sanitizes all text inserts with jsoup,
     * and returns the sanitized Delta JSON string.
     */
    public String validateAndSanitize(String deltaJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(deltaJson);
        } catch (Exception e) {
            throw new ValidationException("Content is not valid JSON");
        }

        if (!root.isObject() || !root.has("ops") || !root.get("ops").isArray()) {
            throw new ValidationException("Content must be a valid Quill Delta with an 'ops' array");
        }

        ArrayNode sanitizedOps = objectMapper.createArrayNode();
        for (JsonNode op : root.get("ops")) {
            if (!op.has("insert") && !op.has("retain") && !op.has("delete")) {
                throw new ValidationException("Invalid Delta op: each op must have insert, retain, or delete");
            }
            if (op.has("insert") && op.get("insert").isTextual()) {
                // Strip all HTML — Delta text inserts must be plain text
                String clean = Jsoup.clean(op.get("insert").asText(), Safelist.none());
                ObjectNode sanitizedOp = objectMapper.createObjectNode();
                sanitizedOp.put("insert", clean);
                if (op.has("attributes")) {
                    sanitizedOp.set("attributes", op.get("attributes"));
                }
                sanitizedOps.add(sanitizedOp);
            } else {
                sanitizedOps.add(op);
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.set("ops", sanitizedOps);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new ValidationException("Failed to serialize sanitized content");
        }
    }

    /**
     * Counts words in a Delta by extracting all string insert values and
     * splitting on whitespace.
     */
    public int countWords(String deltaJson) {
        try {
            JsonNode root = objectMapper.readTree(deltaJson);
            StringBuilder sb = new StringBuilder();
            for (JsonNode op : root.get("ops")) {
                if (op.has("insert") && op.get("insert").isTextual()) {
                    sb.append(op.get("insert").asText()).append(' ');
                }
            }
            String text = sb.toString().trim();
            if (text.isEmpty()) return 0;
            return (int) Arrays.stream(text.split("\\s+")).filter(w -> !w.isEmpty()).count();
        } catch (Exception e) {
            return 0;
        }
    }
}
