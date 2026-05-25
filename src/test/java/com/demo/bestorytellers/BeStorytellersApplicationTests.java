package com.demo.bestorytellers;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Integration test — requires running PostgreSQL and Redis")
@SpringBootTest
class BeStorytellersApplicationTests {

    @Test
    void contextLoads() {
    }

}
