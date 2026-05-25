package com.demo.bestorytellers.story.repository;

import com.demo.bestorytellers.story.entity.Story;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    Optional<Story> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "SELECT s FROM Story s WHERE s.author.id = :authorId AND s.visibility != 'PRIVATE' ORDER BY s.updatedAt DESC",
           countQuery = "SELECT COUNT(s) FROM Story s WHERE s.author.id = :authorId AND s.visibility != 'PRIVATE'")
    Page<Story> findPublicByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);

    @Query(value = "SELECT s FROM Story s WHERE s.visibility = 'PUBLIC' AND s.status != 'DRAFT'",
           countQuery = "SELECT COUNT(s) FROM Story s WHERE s.visibility = 'PUBLIC' AND s.status != 'DRAFT'")
    Page<Story> findAllPublic(Pageable pageable);

    @Query(value = "SELECT s.* FROM stories s WHERE s.search_vector @@ plainto_tsquery('english', :query) AND s.visibility = 'PUBLIC' AND s.status != 'DRAFT'",
           countQuery = "SELECT COUNT(*) FROM stories s WHERE s.search_vector @@ plainto_tsquery('english', :query) AND s.visibility = 'PUBLIC' AND s.status != 'DRAFT'",
           nativeQuery = true)
    Page<Story> searchByText(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT s FROM Story s WHERE s.author.id IN :authorIds AND s.visibility = 'PUBLIC' AND s.status != 'DRAFT' ORDER BY s.updatedAt DESC",
           countQuery = "SELECT COUNT(s) FROM Story s WHERE s.author.id IN :authorIds AND s.visibility = 'PUBLIC' AND s.status != 'DRAFT'")
    Page<Story> findFeedForAuthors(@Param("authorIds") List<UUID> authorIds, Pageable pageable);

    @Modifying
    @Query("UPDATE Story s SET s.viewCount = s.viewCount + :delta WHERE s.id = :storyId")
    void incrementViewCount(@Param("storyId") UUID storyId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Story s SET s.wordCount = s.wordCount + :delta WHERE s.id = :storyId")
    void adjustWordCount(@Param("storyId") UUID storyId, @Param("delta") int delta);
}
