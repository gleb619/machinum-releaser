package machinum.image.cover;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import machinum.exception.AppException;
import machinum.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

import static machinum.image.cover.CoverRenderer.toBytes;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageLoader {

    public static final int MIN_WIDTH = 600;
    public static final int MIN_HEIGHT = 800;

    @SneakyThrows
    public static BufferedImage load(String path) {
        return load(new File(path).getAbsoluteFile());
    }

    @SneakyThrows
    public static BufferedImage load(Path path) {
        return load(path.toFile());
    }

    @SneakyThrows
    public static BufferedImage load(File file) {
        if (!file.exists()) {
            throw new AppException("Image not found: %s", file.getAbsolutePath());
        }
        return ImageIO.read(new FileInputStream(file));
    }

    @SneakyThrows
    public static byte[] loadBytes(File file) {
        var image = load(file);
        return toBytes(image);
    }

    @SneakyThrows
    public static BufferedImage load(Image originImage) {
        var data = new ByteArrayInputStream(originImage.getData());
        return ImageIO.read(data);
    }

    public static BufferedImage mirror(Image originImage) {
        var original = load(originImage);
        return mirror(original);
    }

    public static BufferedImage mirror(File file) {
        var original = load(file);
        return mirror(original);
    }

    public static Image upscale(Image originImage) {
        BufferedImage origin = load(originImage);
        BufferedImage scaled = ImageUpscaler.from(origin)
                .algorithm(ImageUpscaler.ScalingAlgorithm.BICUBIC)
                .backgroundColor(ColorSampler.extract(origin).getDominant())
                .build()
                .optionalScaleToFit(MIN_WIDTH, MIN_HEIGHT);

        return originImage.toBuilder()
                .data(toBytes(scaled))
                .build();
    }

    public static Image optionalResize(Image originImage, ResizeStrategy resizeStrategy, double targetRatio) {
        BufferedImage image = load(originImage);
        double currentRatio = (double) image.getWidth() / image.getHeight();

        if (Math.abs(currentRatio - targetRatio) > 0.01) {
            BufferedImage resizedImage = resizeStrategy.apply(image, targetRatio);
            return originImage.toBuilder()
                    .data(toBytes(resizedImage))
                    .build();
        } else {
            return originImage;
        }
    }

    public static BufferedImage mirror(@NonNull BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        ColorModel cm = original.getColorModel();
        WritableRaster raster = cm.createCompatibleWritableRaster(width, height);
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        BufferedImage mirrored = new BufferedImage(cm, raster, isAlphaPremultiplied, null);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                mirrored.setRGB(width - 1 - x, y, original.getRGB(x, y));
            }
        }

        return mirrored;
    }

}
