package machinum.markdown;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownToHtmlConverterTest {

    @InjectMocks
    MarkdownToHtmlConverter converter;

    @Test
    void testMain() throws IOException {
        var mdString = Files.readString(Path.of("src/test/resources/markdown/sample.md"));
        var htmlString = Files.readString(Path.of("src/test/resources/markdown/sample.html"));

        var result = MarkdownToHtmlConverter.convertMarkdown(mdString);
        MarkdownToHtmlConverter.writeHtmlFile(File.createTempFile("machinum", ".html").toPath(), result.prettyHtml());

        assertThat(result.getWarnings())
                .isEmpty();

        assertThat(result.prettyHtml())
                .isEqualTo(htmlString);
    }

}
