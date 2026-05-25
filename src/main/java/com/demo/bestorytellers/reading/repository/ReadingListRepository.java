package com.demo.bestorytellers.reading.repository;

import com.demo.bestorytellers.reading.entity.ReadingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReadingListRepository extends JpaRepository<ReadingList, UUID> {

    List<ReadingList> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);
}
