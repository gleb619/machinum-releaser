package machinum.image.cover;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static machinum.asserts.BufferedImageAssert.assertThat;

class ImageUpscalerTest {

    private static void saveResult(BufferedImage image, String testName, String fileName) throws IOException {
        //TODO add dev mode, and save only in dev
        var coverFile = File.createTempFile("machinum_%s_%s_".formatted(testName, fileName.replace(".jpg", "")), "_upscale.jpg");
        ImageIO.write(image, "jpeg", coverFile);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/image_1.jpg",
            "covers/image_2_square.jpg",
            "covers/image_3_small_rectangle.jpg"
    })
    void scaleTo1(String path) throws IOException {
        File cover = new File(path);
        BufferedImage bufferedImage = ImageLoader.load(cover);

        // Example 1: Scale to specific dimensions with default settings (bilinear, maintain aspect ratio)
        var result = ImageUpscaler.from(bufferedImage).build()
                .scaleTo(1920, 1080);

        assertThat(result)
                .hasDimensions(1920, 1080)
                .hasType(BufferedImage.TYPE_3BYTE_BGR)
                .isNotEqualTo(bufferedImage);

        saveResult(result, "1", path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/image_1.jpg",
            "covers/image_2_square.jpg",
            "covers/image_3_small_rectangle.jpg"
    })
    void scaleTo2(String path) throws IOException {
        File cover = new File(path);
        BufferedImage bufferedImage = ImageLoader.load(cover);

        // Example 2: Scale by factor with custom algorithm
        var result = ImageUpscaler.from(bufferedImage)
                .algorithm(ImageUpscaler.ScalingAlgorithm.BICUBIC)
                .antiAliasing(true)
                .build()
                .scaleByFactor(2.0);

        assertThat(result)
                .hasType(BufferedImage.TYPE_3BYTE_BGR)
                .isNotEqualTo(bufferedImage);

        saveResult(result, "2", path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/image_1.jpg",
            "covers/image_2_square.jpg",
            "covers/image_3_small_rectangle.jpg"
    })
    void scaleTo3(String path) throws IOException {
        File cover = new File(path);
        BufferedImage bufferedImage = ImageLoader.load(cover);

        // Example 3: Scale to fit within bounds
        var result = ImageUpscaler.from(bufferedImage)
                .maintainAspectRatio(true)
                .backgroundColor(Color.BLACK)
                .build()
                .scaleToFit(800, 600);

        assertThat(result)
                .hasType(BufferedImage.TYPE_3BYTE_BGR)
                .isNotEqualTo(bufferedImage);

        saveResult(result, "3", path);
    }

    /* ============= */

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/image_1.jpg",
            "covers/image_2_square.jpg",
            "covers/image_3_small_rectangle.jpg"
    })
    void scaleTo4(String path) throws IOException {
        File cover = new File(path);
        BufferedImage bufferedImage = ImageLoader.load(cover);

        // Example 4: Fluent API usage
        var result = ImageUpscaler.from(bufferedImage)
                .algorithm(ImageUpscaler.ScalingAlgorithm.NEAREST_NEIGHBOR)
                .maintainAspectRatio(false)
                .build()
                .scaleTo(512, 512);

        assertThat(result)
                .hasDimensions(512, 512)
                .hasType(BufferedImage.TYPE_3BYTE_BGR)
                .isNotEqualTo(bufferedImage);

        saveResult(result, "4", path);
    }

}