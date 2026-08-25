package com.demo.bestorytellers.story.repository;

import com.demo.bestorytellers.story.entity.StoryStar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoryStarRepository extends JpaRepository<StoryStar, StoryStar.StoryStarId> {

    boolean existsByIdUserIdAndIdStoryId(UUID userId, UUID storyId);

    void deleteByIdUserIdAndIdStoryId(UUID userId, UUID storyId);
}
