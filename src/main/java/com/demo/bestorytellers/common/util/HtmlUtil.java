package com.demo.bestorytellers.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class HtmlUtil {

    public String sanitize(String html) {
        return Jsoup.clean(html, Safelist.relaxed());
    }

    public int countWords(String html) {
        String text = Jsoup.parse(html).text().trim();
        if (text.isEmpty()) return 0;
        return (int) Arrays.stream(text.split("\\s+")).filter(w -> !w.isEmpty()).count();
    }
}
