package com.demo.bestorytellers.reading.repository;

import com.demo.bestorytellers.reading.entity.ReadingProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, ReadingProgress.ReadingProgressId> {

    Optional<ReadingProgress> findByIdUserIdAndIdStoryId(UUID userId, UUID storyId);

    @Query(value = "SELECT rp FROM ReadingProgress rp WHERE rp.id.userId = :userId ORDER BY rp.lastReadAt DESC",
           countQuery = "SELECT COUNT(rp) FROM ReadingProgress rp WHERE rp.id.userId = :userId")
    Page<ReadingProgress> findByIdUserIdOrderByLastReadAtDesc(@Param("userId") UUID userId, Pageable pageable);
}
