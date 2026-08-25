package com.demo.bestorytellers.schedule;

import com.demo.bestorytellers.story.service.StoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ViewCountFlushJobTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock StoryService storyService;
    @Mock ValueOperations<String, String> valueOperations;

    @Test
    void flush_neverCallsBlockingKeysCommand() {
        ViewCountFlushJob job = new ViewCountFlushJob(redisTemplate, storyService);

        job.flush();

        then(redisTemplate).should(never()).keys(any());
    }

    @Test
    void flush_whenScanReturnsNoKeys_doesNotCallStoryService() {
        ViewCountFlushJob job = new ViewCountFlushJob(redisTemplate, storyService);

        job.flush();

        then(storyService).shouldHaveNoInteractions();
    }
}
