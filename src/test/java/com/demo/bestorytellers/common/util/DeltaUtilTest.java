package com.demo.bestorytellers.common.util;

import com.demo.bestorytellers.common.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaUtilTest {

    private final DeltaUtil deltaUtil = new DeltaUtil(new ObjectMapper());

    // --- validateAndSanitize ---

    @Test
    void validateAndSanitize_whenValidDelta_thenReturnsSanitizedJson() {
        String delta = "{\"ops\":[{\"insert\":\"Hello world\\n\"}]}";
        String result = deltaUtil.validateAndSanitize(delta);
        assertNotNull(result);
        assertTrue(result.contains("Hello world"));
    }

    @Test
    void validateAndSanitize_whenNotJson_thenThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            deltaUtil.validateAndSanitize("not json at all"));
    }

    @Test
    void validateAndSanitize_whenMissingOpsArray_thenThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            deltaUtil.validateAndSanitize("{\"content\":\"hello\"}"));
    }

    @Test
    void validateAndSanitize_whenOpsIsNotArray_thenThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            deltaUtil.validateAndSanitize("{\"ops\":\"not-an-array\"}"));
    }

    @Test
    void validateAndSanitize_whenOpMissingRequiredField_thenThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            deltaUtil.validateAndSanitize("{\"ops\":[{\"attributes\":{\"bold\":true}}]}"));
    }

    @Test
    void validateAndSanitize_whenInsertContainsScriptTag_thenStripsHtml() {
        String delta = "{\"ops\":[{\"insert\":\"<script>alert('xss')</script>Hello\"}]}";
        String result = deltaUtil.validateAndSanitize(delta);
        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void validateAndSanitize_whenDeltaHasAttributes_thenPreservesAttributes() {
        String delta = "{\"ops\":[{\"insert\":\"Bold\",\"attributes\":{\"bold\":true}}]}";
        String result = deltaUtil.validateAndSanitize(delta);
        assertTrue(result.contains("bold"));
        assertTrue(result.contains("Bold"));
    }

    @Test
    void validateAndSanitize_whenInsertIsEmbed_thenPassesThrough() {
        String delta = "{\"ops\":[{\"insert\":{\"image\":\"https://example.com/img.png\"}}]}";
        String result = deltaUtil.validateAndSanitize(delta);
        assertNotNull(result);
        assertTrue(result.contains("image"));
    }

    // --- countWords ---

    @Test
    void countWords_whenSimpleSentence_thenReturnsCorrectCount() {
        String delta = "{\"ops\":[{\"insert\":\"Hello world foo\\n\"}]}";
        assertEquals(3, deltaUtil.countWords(delta));
    }

    @Test
    void countWords_whenMultipleOps_thenCombinesText() {
        String delta = "{\"ops\":[{\"insert\":\"Hello \"},{\"insert\":\"world\"}]}";
        assertEquals(2, deltaUtil.countWords(delta));
    }

    @Test
    void countWords_whenOnlyEmbeds_thenReturnsZero() {
        String delta = "{\"ops\":[{\"insert\":{\"image\":\"url\"}}]}";
        assertEquals(0, deltaUtil.countWords(delta));
    }

    @Test
    void countWords_whenEmptyInsert_thenReturnsZero() {
        String delta = "{\"ops\":[{\"insert\":\"   \\n  \"}]}";
        assertEquals(0, deltaUtil.countWords(delta));
    }

    @Test
    void countWords_whenInvalidJson_thenReturnsZero() {
        assertEquals(0, deltaUtil.countWords("not json"));
    }
}
