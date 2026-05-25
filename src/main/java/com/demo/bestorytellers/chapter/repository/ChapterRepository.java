package com.demo.bestorytellers.chapter.repository;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    Optional<Chapter> findByStoryIdAndChapterNumber(UUID storyId, int chapterNumber);

    @Query(value = "SELECT c FROM Chapter c WHERE c.story.id = :storyId AND c.status = :status ORDER BY c.chapterNumber ASC",
           countQuery = "SELECT COUNT(c) FROM Chapter c WHERE c.story.id = :storyId AND c.status = :status")
    Page<Chapter> findByStoryIdAndStatusOrderByChapterNumberAsc(
        @Param("storyId") UUID storyId, @Param("status") ChapterStatus status, Pageable pageable);

    @Query(value = "SELECT c FROM Chapter c WHERE c.story.id = :storyId ORDER BY c.chapterNumber ASC",
           countQuery = "SELECT COUNT(c) FROM Chapter c WHERE c.story.id = :storyId")
    Page<Chapter> findByStoryIdOrderByChapterNumberAsc(@Param("storyId") UUID storyId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(c.chapterNumber), 0) FROM Chapter c WHERE c.story.id = :storyId")
    int findMaxChapterNumber(@Param("storyId") UUID storyId);

    List<Chapter> findByStatusAndPublishedAtLessThanEqual(ChapterStatus status, Instant now);
}
