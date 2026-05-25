package com.demo.bestorytellers.reading.service;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.common.dto.PageResponse;
import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ForbiddenException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.reading.dto.CreateListRequest;
import com.demo.bestorytellers.reading.dto.ListItemResponse;
import com.demo.bestorytellers.reading.dto.ProgressRequest;
import com.demo.bestorytellers.reading.dto.ProgressResponse;
import com.demo.bestorytellers.reading.dto.ReadingHistoryItem;
import com.demo.bestorytellers.reading.dto.ReadingListResponse;
import com.demo.bestorytellers.reading.entity.ReadingList;
import com.demo.bestorytellers.reading.entity.ReadingListItem;
import com.demo.bestorytellers.reading.entity.ReadingProgress;
import com.demo.bestorytellers.reading.repository.ReadingListItemRepository;
import com.demo.bestorytellers.reading.repository.ReadingListRepository;
import com.demo.bestorytellers.reading.repository.ReadingProgressRepository;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReadingService {

    private final ReadingListRepository listRepository;
    private final ReadingListItemRepository itemRepository;
    private final ReadingProgressRepository progressRepository;
    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;

    public ReadingService(ReadingListRepository listRepository,
                          ReadingListItemRepository itemRepository,
                          ReadingProgressRepository progressRepository,
                          StoryRepository storyRepository,
                          ChapterRepository chapterRepository,
                          UserRepository userRepository) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.progressRepository = progressRepository;
        this.storyRepository = storyRepository;
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ReadingListResponse> getLists(UUID userId) {
        return listRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(l -> new ReadingListResponse(
                l.getId(), l.getName(), l.isDefaultList(),
                itemRepository.countByIdListId(l.getId()), l.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Transactional
    public ReadingListResponse createList(UUID userId, CreateListRequest request) {
        long count = listRepository.countByUserId(userId);
        if (count >= 20) {
            throw new ValidationException("Maximum 20 reading lists allowed");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ReadingList list = new ReadingList(user, request.name(), false);
        ReadingList saved = listRepository.save(list);
        return new ReadingListResponse(saved.getId(), saved.getName(), false, 0, saved.getCreatedAt());
    }

    @Transactional
    public ListItemResponse addToList(UUID listId, UUID storyId, UUID userId) {
        ReadingList list = listRepository.findById(listId)
            .orElseThrow(() -> new ResourceNotFoundException("Reading list not found: " + listId));
        if (!list.getUser().getId().equals(userId)) {
            throw new ForbiddenException("List does not belong to you");
        }
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));
        if (itemRepository.existsByIdListIdAndIdStoryId(listId, storyId)) {
            throw new ConflictException("Story already in list");
        }
        ReadingListItem item = new ReadingListItem(list, story);
        itemRepository.save(item);
        return new ListItemResponse(listId, storyId, item.getAddedAt());
    }

    @Transactional
    public void removeFromList(UUID listId, UUID storyId, UUID userId) {
        ReadingList list = listRepository.findById(listId)
            .orElseThrow(() -> new ResourceNotFoundException("Reading list not found: " + listId));
        if (!list.getUser().getId().equals(userId)) {
            throw new ForbiddenException("List does not belong to you");
        }
        itemRepository.deleteByIdListIdAndIdStoryId(listId, storyId);
    }

    @Transactional
    public ProgressResponse upsertProgress(UUID userId, UUID storyId, ProgressRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));
        Chapter chapter = chapterRepository.findById(request.chapterId())
            .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + request.chapterId()));

        ReadingProgress progress = progressRepository
            .findByIdUserIdAndIdStoryId(userId, storyId)
            .map(p -> {
                p.update(chapter, (short) request.progressPct());
                return p;
            })
            .orElse(new ReadingProgress(user, story, chapter, (short) request.progressPct()));

        ReadingProgress saved = progressRepository.save(progress);
        return new ProgressResponse(storyId, request.chapterId(), saved.getProgressPct(), saved.getLastReadAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingHistoryItem> getHistory(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        return PageResponse.from(
            progressRepository.findByIdUserIdOrderByLastReadAtDesc(userId, pageable)
                .map(p -> {
                    Story s = p.getStory();
                    Chapter c = p.getChapter();
                    var storyRef = new ReadingHistoryItem.StoryRef(
                        s.getId(), s.getSlug(), s.getTitle(), s.getCoverImageUrl());
                    ReadingHistoryItem.ChapterRef chapterRef = c != null
                        ? new ReadingHistoryItem.ChapterRef(c.getId(), c.getChapterNumber(), c.getTitle())
                        : null;
                    return new ReadingHistoryItem(storyRef, chapterRef, p.getProgressPct(), p.getLastReadAt());
                }));
    }
}
