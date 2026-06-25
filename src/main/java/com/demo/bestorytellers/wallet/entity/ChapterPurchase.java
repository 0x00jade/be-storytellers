package com.demo.bestorytellers.wallet.entity;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "chapter_purchases")
public class ChapterPurchase {

    @EmbeddedId
    private ChapterPurchaseId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("chapterId")
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(name = "tx_id")
    private UUID txId;

    @Column(name = "purchased_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant purchasedAt;

    protected ChapterPurchase() {}

    public ChapterPurchase(User user, Chapter chapter, UUID txId) {
        this.id = new ChapterPurchaseId(user.getId(), chapter.getId());
        this.user = user;
        this.chapter = chapter;
        this.txId = txId;
        this.purchasedAt = Instant.now();
    }

    public ChapterPurchaseId getId() { return id; }
    public User getUser() { return user; }
    public Chapter getChapter() { return chapter; }
    public UUID getTxId() { return txId; }
    public Instant getPurchasedAt() { return purchasedAt; }

    @Embeddable
    public static class ChapterPurchaseId implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "chapter_id")
        private UUID chapterId;

        protected ChapterPurchaseId() {}

        public ChapterPurchaseId(UUID userId, UUID chapterId) {
            this.userId = userId;
            this.chapterId = chapterId;
        }

        public UUID getUserId() { return userId; }
        public UUID getChapterId() { return chapterId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChapterPurchaseId p)) return false;
            return Objects.equals(userId, p.userId) && Objects.equals(chapterId, p.chapterId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, chapterId);
        }
    }
}
