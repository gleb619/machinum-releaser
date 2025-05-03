package machinum.image;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TriangleWrapperTest {

    TriangleWrapper wrapper = new TriangleWrapper("triangle");

    @Test
    public void testMain() throws IOException, InterruptedException {
        var outputPath = File.createTempFile("machinum", ".jpg").getAbsolutePath();
        var inputPath = Path.of("src/test/resources/images/sample1.jpg").toFile().getAbsolutePath();

        try {
            wrapper.runTriangle(TriangleWrapper.defaultSettings(inputPath, outputPath));
            assertThat(new File(outputPath))
                    .exists();
        } finally {
            new File(outputPath).delete();
        }
    }

}
