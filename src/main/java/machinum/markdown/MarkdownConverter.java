package machinum.markdown;

import lombok.extern.slf4j.Slf4j;
import machinum.chapter.Chapter;

import java.util.Objects;

@Slf4j
public class MarkdownConverter {

    public String toMarkdown(Chapter chapter) {
        String translatedText = resolveText(chapter);
        String text = translatedText
                .replaceAll("(?m)$", "  ");

        return """
                # %s \s
                 \s
                %s \s
                """.formatted(chapter.getTranslatedTitle(), text);
    }

    private static String resolveText(Chapter chapter) {
        String translatedText = chapter.getTranslatedText();
        if(Objects.isNull(translatedText) || translatedText.isBlank()) {
            return chapter.get(Chapter.TEXT, "");
        }

        return translatedText;
    }

}
