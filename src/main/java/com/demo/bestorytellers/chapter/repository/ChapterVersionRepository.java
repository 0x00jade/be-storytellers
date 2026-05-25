package com.demo.bestorytellers.chapter.repository;

import com.demo.bestorytellers.chapter.entity.ChapterVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterVersionRepository extends JpaRepository<ChapterVersion, UUID> {

    List<ChapterVersion> findByChapterIdOrderByVersionNumberDesc(UUID chapterId);

    Optional<ChapterVersion> findByChapterIdAndVersionNumber(UUID chapterId, int versionNumber);

    @Query("SELECT COALESCE(MAX(cv.versionNumber), 0) FROM ChapterVersion cv WHERE cv.chapter.id = :chapterId")
    int findMaxVersionNumber(@Param("chapterId") UUID chapterId);

    @Query("SELECT cv FROM ChapterVersion cv WHERE cv.chapter.id = :chapterId AND cv.published = false ORDER BY cv.versionNumber DESC")
    List<ChapterVersion> findDraftVersionsOrderedDesc(@Param("chapterId") UUID chapterId);
}
