package com.demo.bestorytellers.wallet.repository;

import com.demo.bestorytellers.wallet.entity.ChapterPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChapterPurchaseRepository
        extends JpaRepository<ChapterPurchase, ChapterPurchase.ChapterPurchaseId> {

    boolean existsByIdUserIdAndIdChapterId(UUID userId, UUID chapterId);
}
