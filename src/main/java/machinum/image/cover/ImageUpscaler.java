package machinum.image.cover;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Image upscaler for Java 21 with Lombok support
 * Provides clean API for scaling images with various algorithms
 */
@Data
@Slf4j
@Builder
public class ImageUpscaler {

    private final BufferedImage sourceImage;

    @Builder.Default
    private final ScalingAlgorithm algorithm = ScalingAlgorithm.BILINEAR;

    @Builder.Default
    private final boolean maintainAspectRatio = true;

    @Builder.Default
    private final Color backgroundColor = Color.WHITE;

    @Builder.Default
    private final boolean antiAliasing = true;

    /**
     * Create upscaler from file path
     */
    public static ImageUpscalerBuilder fromFile(String filePath) throws IOException {
        return fromFile(Path.of(filePath));
    }

    /**
     * Create upscaler from Path
     */
    public static ImageUpscalerBuilder fromFile(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Could not read image from: " + path);
        }
        return from(image);
    }

    /**
     * Create upscaler from File
     */
    public static ImageUpscalerBuilder fromFile(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Could not read image from: " + file.getPath());
        }
        return from(image);
    }

    /**
     * Create upscaler from BufferedImage
     */
    public static ImageUpscalerBuilder from(@NonNull BufferedImage image) {
        return ImageUpscaler.builder().sourceImage(image);
    }

    /**
     * Scale or return same image
     */
    public BufferedImage optionalScaleToFit(int targetWidth, int targetHeight) {
        if (targetWidth > sourceImage.getWidth() || targetHeight > sourceImage.getHeight()) {
            log.debug("Image doesn't fit to minimal settings, image will be scaled up");
            return scaleToFit(targetWidth, targetHeight);
        }

        return sourceImage;
    }

    public BufferedImage scaleTo(int targetWidth, int targetHeight) {
        return new ImageRenderer(this).scaleTo(targetWidth, targetHeight);
    }

    public BufferedImage scaleByFactor(double factor) {
        return new ImageRenderer(this).scaleByFactor(factor);
    }

    public BufferedImage scaleToFit(int maxWidth, int maxHeight) {
        return new ImageRenderer(this).scaleToFit(maxWidth, maxHeight);
    }

    /**
     * Scaling algorithms available
     */
    @Getter
    @RequiredArgsConstructor
    public enum ScalingAlgorithm {

        NEAREST_NEIGHBOR(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
        BILINEAR(RenderingHints.VALUE_INTERPOLATION_BILINEAR),
        BICUBIC(RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        private final Object renderingHint;

    }

    @RequiredArgsConstructor
    public static class ImageRenderer {

        private final ImageUpscaler imageUpscaler;

        /**
         * Scale image to exact dimensions
         */
        public BufferedImage scaleTo(int targetWidth, int targetHeight) {
            if (imageUpscaler.sourceImage == null) {
                throw new AppException("Source image cannot be null");
            }

            var dimensions = imageUpscaler.maintainAspectRatio
                    ? calculateAspectRatioDimensions(targetWidth, targetHeight)
                    : new Dimension(targetWidth, targetHeight);

            return performScaling(dimensions.width, dimensions.height, targetWidth, targetHeight);
        }

        /**
         * Scale image by a factor (e.g., 2.0 for double size)
         */
        public BufferedImage scaleByFactor(double factor) {
            if (factor <= 0) {
                throw new IllegalArgumentException("Scale factor must be positive");
            }

            int newWidth = (int) (imageUpscaler.sourceImage.getWidth() * factor);
            int newHeight = (int) (imageUpscaler.sourceImage.getHeight() * factor);

            return performScaling(newWidth, newHeight, newWidth, newHeight);
        }

        /**
         * Scale to fit within maximum dimensions while maintaining aspect ratio
         */
        public BufferedImage scaleToFit(int maxWidth, int maxHeight) {
            var dimensions = calculateFitDimensions(maxWidth, maxHeight);
            return performScaling(dimensions.width, dimensions.height, dimensions.width, dimensions.height);
        }

        private BufferedImage performScaling(int scaledWidth, int scaledHeight, int canvasWidth, int canvasHeight) {
            // Create target image with proper color model
            BufferedImage scaledImage = new BufferedImage(
                    canvasWidth,
                    canvasHeight,
                    imageUpscaler.sourceImage.getType() == BufferedImage.TYPE_CUSTOM
                            ? BufferedImage.TYPE_INT_ARGB
                            : imageUpscaler.sourceImage.getType()
            );

            Graphics2D g2d = scaledImage.createGraphics();

            try {
                // Set rendering hints for quality
                setupRenderingHints(g2d);

                // Fill background if maintaining aspect ratio and image doesn't fill canvas
                if (imageUpscaler.maintainAspectRatio && (scaledWidth != canvasWidth || scaledHeight != canvasHeight)) {
                    g2d.setColor(imageUpscaler.backgroundColor);
                    g2d.fillRect(0, 0, canvasWidth, canvasHeight);
                }

                // Calculate centering offset for aspect ratio maintenance
                int x = (canvasWidth - scaledWidth) / 2;
                int y = (canvasHeight - scaledHeight) / 2;

                // Perform the scaling
                g2d.drawImage(imageUpscaler.sourceImage, x, y, scaledWidth, scaledHeight, null);

            } finally {
                g2d.dispose();
            }

            log.debug("Scaled image from {}x{} to {}x{} using {}",
                    imageUpscaler.sourceImage.getWidth(), imageUpscaler.sourceImage.getHeight(),
                    scaledWidth, scaledHeight, imageUpscaler.algorithm);

            return scaledImage;
        }

        private void setupRenderingHints(Graphics2D g2d) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, imageUpscaler.algorithm.getRenderingHint());

            if (imageUpscaler.antiAliasing) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            } else {
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            }

            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        }

        private Dimension calculateAspectRatioDimensions(int targetWidth, int targetHeight) {
            int sourceWidth = imageUpscaler.sourceImage.getWidth();
            int sourceHeight = imageUpscaler.sourceImage.getHeight();

            double sourceAspect = (double) sourceWidth / sourceHeight;
            double targetAspect = (double) targetWidth / targetHeight;

            int newWidth, newHeight;

            if (sourceAspect > targetAspect) {
                // Source is wider, fit to width
                newWidth = targetWidth;
                newHeight = (int) (targetWidth / sourceAspect);
            } else {
                // Source is taller, fit to height
                newHeight = targetHeight;
                newWidth = (int) (targetHeight * sourceAspect);
            }

            return new Dimension(newWidth, newHeight);
        }

        private Dimension calculateFitDimensions(int maxWidth, int maxHeight) {
            int sourceWidth = imageUpscaler.sourceImage.getWidth();
            int sourceHeight = imageUpscaler.sourceImage.getHeight();

            if (sourceWidth <= maxWidth && sourceHeight <= maxHeight) {
                return new Dimension(sourceWidth, sourceHeight);
            }

            double scaleX = (double) maxWidth / sourceWidth;
            double scaleY = (double) maxHeight / sourceHeight;
            double scale = Math.min(scaleX, scaleY);

            return new Dimension(
                    (int) (sourceWidth * scale),
                    (int) (sourceHeight * scale)
            );
        }

    }

}
