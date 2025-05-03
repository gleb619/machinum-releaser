package machinum.markdown;

import lombok.extern.slf4j.Slf4j;
import machinum.chapter.Chapter;

@Slf4j
public class MarkdownConverter {

    public String toMarkdown(Chapter chapter) {
        return """
                # %s
                                
                %s
                """.formatted(chapter.getTranslatedTitle(), chapter.getTranslatedText());
    }

}
