package com.demo.bestorytellers.schedule;

import com.demo.bestorytellers.chapter.dto.PublishRequest;
import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.chapter.service.ChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ChapterPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(ChapterPublisherJob.class);

    private final ChapterRepository chapterRepository;
    private final ChapterService chapterService;

    public ChapterPublisherJob(ChapterRepository chapterRepository,
                               ChapterService chapterService) {
        this.chapterRepository = chapterRepository;
        this.chapterService = chapterService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void publishScheduled() {
        List<Chapter> due = chapterRepository.findByStatusAndPublishedAtLessThanEqual(
            ChapterStatus.SCHEDULED, Instant.now());
        if (due.isEmpty()) return;

        int published = 0;
        for (Chapter chapter : due) {
            try {
                String slug = chapter.getStory().getSlug();
                UUID authorId = chapter.getStory().getAuthor().getId();
                chapterService.publish(slug, chapter.getChapterNumber(),
                    authorId, new PublishRequest(null));
                published++;
            } catch (Exception e) {
                log.error("Failed to auto-publish chapter {}: {}", chapter.getId(), e.getMessage());
            }
        }
        log.info("Auto-published {} chapters", published);
    }
}
