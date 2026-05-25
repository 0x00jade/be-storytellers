package com.demo.bestorytellers.reading.repository;

import com.demo.bestorytellers.reading.entity.ReadingListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReadingListItemRepository extends JpaRepository<ReadingListItem, ReadingListItem.ReadingListItemId> {

    boolean existsByIdListIdAndIdStoryId(UUID listId, UUID storyId);

    void deleteByIdListIdAndIdStoryId(UUID listId, UUID storyId);

    long countByIdListId(UUID listId);
}
