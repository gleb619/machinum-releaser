package machinum.markdown;

import machinum.chapter.Chapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MarkdownConverterTest {

    MarkdownConverter markdownConverter = new MarkdownConverter();

    @Test
    void testToMarkdown() {
        var chapter = new Chapter(1, "translatedTitle", """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.  
                Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.  
                Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.  
                Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.""");
        var result = markdownConverter.toMarkdown(chapter);

        Assertions.assertEquals("""
                # translatedTitle
                                                                                                                              
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. \s
                Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. \s
                Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. \s
                Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. \s
                """, result);
    }

}
