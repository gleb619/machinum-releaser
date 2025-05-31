package machinum.image.cover;

import machinum.image.Image;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

public class CoverServiceTest {

    CoverService coverService = new CoverService();

    private static void saveTempResult(FileResult fileResult, Image result) throws IOException {
        //TODO add dev mode, and save only in dev
        var coverFile = File.createTempFile("machinum_%s_".formatted(fileResult.fileName()), "_cover.jpg");
        ImageIO.write(ImageLoader.load(result), "jpeg", coverFile);
    }

    private static FileResult extracted(String path) throws IOException {
        File cover = new File(path);
        byte[] coverContent = Files.readAllBytes(cover.toPath());
        LocalDateTime dateTime = LocalDateTime.of(2025, Month.MAY, 27, 22, 0, 0);
        Image originImage = new Image("id", "name", "contentType", coverContent, dateTime);
        return new FileResult(cover, originImage);
    }

    /* ============= */

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/image_1.jpg",
            "covers/image_2_square.jpg",
            "covers/image_3_small_rectangle.jpg"
    })
    public void testGenerate(String path) throws Exception {
        FileResult fileResult = extracted(path);

        Image result = coverService.generate(fileResult.originImage());

        assertThat(result)
                .isNotNull();

        saveTempResult(fileResult, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/image_1.jpg",
            "covers/image_2_square.jpg",
            "covers/image_3_small_rectangle.jpg"
    })
    public void testGenerateBookCover(String path) throws Exception {
        FileResult fileResult = extracted(path);

        Image result = coverService.generateBookCover(fileResult.originImage(), new CoverService.CoverInfo(
                "1",
                "Blah 123",
                "My Brand",
                "https://t.me/my_novel",
                "@my_novel",
                "Free palestine"
        ));

        assertThat(result)
                .isNotNull();

        saveTempResult(fileResult, result);
    }

    private record FileResult(File cover, Image originImage) {

        public String fileName() {
            return cover().getName().replace(".jpg", "");
        }

    }

}
