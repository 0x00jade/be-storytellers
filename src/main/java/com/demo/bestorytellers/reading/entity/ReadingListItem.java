package com.demo.bestorytellers.reading.entity;

import com.demo.bestorytellers.story.entity.Story;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reading_list_items")
public class ReadingListItem {

    @EmbeddedId
    private ReadingListItemId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("listId")
    @JoinColumn(name = "list_id")
    private ReadingList list;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("storyId")
    @JoinColumn(name = "story_id")
    private Story story;

    @Column(name = "added_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant addedAt;

    protected ReadingListItem() {}

    public ReadingListItem(ReadingList list, Story story) {
        this.id = new ReadingListItemId(list.getId(), story.getId());
        this.list = list;
        this.story = story;
        this.addedAt = Instant.now();
    }

    public UUID getListId() { return id.getListId(); }
    public UUID getStoryId() { return id.getStoryId(); }
    public ReadingList getList() { return list; }
    public Story getStory() { return story; }
    public Instant getAddedAt() { return addedAt; }

    @Embeddable
    public static class ReadingListItemId implements Serializable {

        @Column(name = "list_id")
        private UUID listId;

        @Column(name = "story_id")
        private UUID storyId;

        protected ReadingListItemId() {}

        public ReadingListItemId(UUID listId, UUID storyId) {
            this.listId = listId;
            this.storyId = storyId;
        }

        public UUID getListId() { return listId; }
        public UUID getStoryId() { return storyId; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReadingListItemId r)) return false;
            return Objects.equals(listId, r.listId) && Objects.equals(storyId, r.storyId);
        }

        @Override
        public int hashCode() { return Objects.hash(listId, storyId); }
    }
}
