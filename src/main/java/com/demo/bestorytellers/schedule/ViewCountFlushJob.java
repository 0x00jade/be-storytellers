package com.demo.bestorytellers.schedule;

import com.demo.bestorytellers.story.service.StoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ViewCountFlushJob {

    private static final Logger log = LoggerFactory.getLogger(ViewCountFlushJob.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final StoryService storyService;

    public ViewCountFlushJob(RedisTemplate<String, String> redisTemplate,
                             StoryService storyService) {
        this.redisTemplate = redisTemplate;
        this.storyService = storyService;
    }

    @Scheduled(fixedDelay = 300_000)
    public void flush() {
        List<String> keys = scanViewKeys();
        if (keys.isEmpty()) return;

        int count = 0;
        for (String key : keys) {
            String value = redisTemplate.opsForValue().getAndDelete(key);
            if (value == null) continue;
            try {
                UUID storyId = UUID.fromString(key.replace("story:views:", ""));
                long delta = Long.parseLong(value);
                storyService.applyViewCountDelta(storyId, delta);
                count++;
            } catch (Exception e) {
                log.warn("Failed to flush view count for key {}: {}", key, e.getMessage());
            }
        }
        log.info("Flushed view counts for {} stories", count);
    }

    private List<String> scanViewKeys() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match("story:views:*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception e) {
            log.error("Redis SCAN failed during view count flush", e);
        }
        return keys;
    }
}
