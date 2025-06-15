package machinum.novel;

import machinum.novel.NovelScraper.Novel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NovelScraperTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void mainTest() {
        // Search functionality
        try {
            List<NovelScraper.SearchResult> results = NovelScraper.search("cultivation");
            results.forEach(System.out::println);
        } catch (NovelScraper.ScrapeException e) {
            e.printStackTrace();
        }

        // Custom configuration
        NovelScraper.ScraperConfig config = NovelScraper.ScraperConfig.builder()
                .timeout(10000)
                .maxRetries(5)
                .delayMs(1500)
                .build();

        try {
            Novel customNovel = NovelScraper.forBook("novel-id")
                    .performScrape();
        } catch (NovelScraper.ScrapeException e) {
            e.printStackTrace();
        }
    }

}