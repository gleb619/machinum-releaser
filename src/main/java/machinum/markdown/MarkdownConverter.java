package machinum.markdown;

import lombok.extern.slf4j.Slf4j;
import machinum.chapter.Chapter;

@Slf4j
public class MarkdownConverter {

    public String toMarkdown(Chapter chapter) {
        String text = chapter.getTranslatedText()
                .replaceAll("(?m)$", "  ");

        return """
                # %s \s
                 \s
                %s \s
                """.formatted(chapter.getTranslatedTitle(), text);
    }

}
