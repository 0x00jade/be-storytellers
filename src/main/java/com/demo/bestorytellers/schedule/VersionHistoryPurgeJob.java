package com.demo.bestorytellers.schedule;

import com.demo.bestorytellers.chapter.entity.ChapterVersion;
import com.demo.bestorytellers.chapter.repository.ChapterVersionRepository;
import com.demo.bestorytellers.common.util.S3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class VersionHistoryPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(VersionHistoryPurgeJob.class);
    private static final int MAX_DRAFT_VERSIONS_PER_CHAPTER = 50;

    private final ChapterVersionRepository versionRepository;
    private final S3Util s3Util;

    public VersionHistoryPurgeJob(ChapterVersionRepository versionRepository, S3Util s3Util) {
        this.versionRepository = versionRepository;
        this.s3Util = s3Util;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purge() {
        List<ChapterVersion> allDraftVersions = versionRepository.findAll().stream()
            .filter(v -> !v.isPublished())
            .collect(Collectors.toList());

        Map<UUID, List<ChapterVersion>> byChapter = allDraftVersions.stream()
            .collect(Collectors.groupingBy(v -> v.getChapter().getId()));

        int purgedCount = 0;
        for (Map.Entry<UUID, List<ChapterVersion>> entry : byChapter.entrySet()) {
            List<ChapterVersion> sorted = entry.getValue().stream()
                .sorted(Comparator.comparingInt(ChapterVersion::getVersionNumber).reversed())
                .collect(Collectors.toList());

            if (sorted.size() <= MAX_DRAFT_VERSIONS_PER_CHAPTER) continue;

            List<ChapterVersion> toDelete = sorted.subList(
                MAX_DRAFT_VERSIONS_PER_CHAPTER, sorted.size());

            for (ChapterVersion v : toDelete) {
                try {
                    s3Util.deleteObject(v.getContentUrl());
                } catch (Exception e) {
                    log.warn("Could not delete S3 object {}: {}", v.getContentUrl(), e.getMessage());
                }
                versionRepository.delete(v);
                purgedCount++;
            }
        }
        log.info("Purged {} old draft chapter versions", purgedCount);
    }
}
