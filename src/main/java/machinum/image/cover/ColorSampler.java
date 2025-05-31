package machinum.image.cover;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static machinum.image.cover.BrandMark.CornerPosition.TOP_LEFT;
import static machinum.image.cover.BrandMark.CornerPosition.TOP_RIGHT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ColorSampler {

    public static Color calculateTextColor(Color backgroundColor, boolean isTitle) {
        double luminance = calculateLuminance(backgroundColor);

        // Different contrast requirements for spine vs body
        double contrastThreshold = 4.5;

        // Title text needs higher contrast than author text
        if (isTitle) {
            contrastThreshold += 1.0;
        }

        // Return white or black based on background luminance
        if (luminance > 0.5) {
            return new Color(0, 0, 0, (int) (255 * Math.min(1.0, contrastThreshold / 5.0)));
        } else {
            return new Color(255, 255, 255, (int) (255 * Math.min(1.0, contrastThreshold / 5.0)));
        }
    }

    public static Color calculateOptimalTextColor(BufferedImage backgroundRegion, boolean isTitle) {
        // Sample multiple points in the region
        int samples = 25;
        int totalR = 0, totalG = 0, totalB = 0;
        int width = backgroundRegion.getWidth();
        int height = backgroundRegion.getHeight();

        for (int i = 0; i < samples; i++) {
            int x = (i % 5) * width / 5 + width / 10;
            int y = (i / 5) * height / 5 + height / 10;

            if (x < width && y < height) {
                Color pixel = new Color(backgroundRegion.getRGB(x, y));
                totalR += pixel.getRed();
                totalG += pixel.getGreen();
                totalB += pixel.getBlue();
            }
        }

        Color averageColor = new Color(totalR / samples, totalG / samples, totalB / samples);
        return calculateTextColor(averageColor, isTitle);
    }

    private static double calculateLuminance(Color color) {
        double r = color.getRed() / 255.0;
        double g = color.getGreen() / 255.0;
        double b = color.getBlue() / 255.0;

        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static ColorPalette extract(BufferedImage image) {
        Map<Integer, Integer> colorCount = getColorCounts(image, 0, 0, image.getWidth(), image.getHeight());

        int dominantRgb = getDominantColor(colorCount);

        Color dominant = new Color(dominantRgb);

        Color topLeft = getDominantColorByRegion(image, TOP_LEFT);
        Color topRight = getDominantColorByRegion(image, TOP_RIGHT);

        return ColorPalette.builder()
                .dominant(dominant)
                .topLeftColor(topLeft)
                .topRightColor(topRight)
                .darkVariant(darken(dominant, 0.3f))
                .lightVariant(lighten(dominant, 0.3f))
                .build();
    }

    public static Color darken(Color color, float factor) {
        return new Color(
                Math.max(0, (int) (color.getRed() * (1 - factor))),
                Math.max(0, (int) (color.getGreen() * (1 - factor))),
                Math.max(0, (int) (color.getBlue() * (1 - factor)))
        );
    }

    public static Color lighten(Color color, float factor) {
        return new Color(
                Math.min(255, (int) (color.getRed() + (255 - color.getRed()) * factor)),
                Math.min(255, (int) (color.getGreen() + (255 - color.getGreen()) * factor)),
                Math.min(255, (int) (color.getBlue() + (255 - color.getBlue()) * factor))
        );
    }

    public static Color transparentColor() {
        return new Color(255, 255, 255, 0);
    }

    public static Color newAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static String asHex(Color color) {
        return "#" + Integer.toHexString(color.getRGB()).substring(2);
    }

    private static Color getDominantColorByRegion(BufferedImage image, BrandMark.CornerPosition position) {
        int width = image.getWidth();
        int height = image.getHeight();
        int regionWidth = width / 4;
        int regionHeight = height / 4;
        int startX = 0;
        int startY = switch (position) {
            case TOP_LEFT -> 0;
            case TOP_RIGHT -> {
                startX = width - regionWidth;
                yield 0;
            }
        };

        Map<Integer, Integer> regionColorCount = getColorCounts(image, startX, startY, regionWidth, regionHeight);
        int dominantRgb = getDominantColor(regionColorCount);
        return new Color(dominantRgb);
    }

    private static int getDominantColor(Map<Integer, Integer> colorCount) {
        return colorCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0x808080);
    }

    private static Map<Integer, Integer> getColorCounts(BufferedImage image, int startX, int startY, int width, int height) {
        Map<Integer, Integer> colorCount = new HashMap<>();
        for (int y = startY; y < startY + height; y += 2) {
            for (int x = startX; x < startX + width; x += 2) {
                if (x < image.getWidth() && y < image.getHeight()) {
                    int rgb = image.getRGB(x, y);
                    colorCount.merge(rgb, 1, Integer::sum);
                }
            }
        }
        return colorCount;
    }

    @Data
    @Builder(toBuilder = true)
    public static class ColorPalette {

        @Builder.Default
        private Color dominant = new Color(10, 10, 10);
        @Builder.Default
        private Color topLeftColor = new Color(15, 15, 15);
        @Builder.Default
        private Color topRightColor = new Color(15, 15, 15);
        @Builder.Default
        private Color darkVariant = Color.BLACK;
        @Builder.Default
        private Color lightVariant = Color.WHITE;

        public ColorPalette invert() {
            return ColorPalette.builder()
                    .dominant(new Color(255 - dominant.getRed(), 255 - dominant.getGreen(), 255 - dominant.getBlue()))
                    .topLeftColor(new Color(255 - topLeftColor.getRed(), 255 - topLeftColor.getGreen(), 255 - topLeftColor.getBlue()))
                    .topRightColor(new Color(255 - topRightColor.getRed(), 255 - topRightColor.getGreen(), 255 - topRightColor.getBlue()))
                    .darkVariant(new Color(255 - darkVariant.getRed(), 255 - darkVariant.getGreen(), 255 - darkVariant.getBlue()))
                    .lightVariant(new Color(255 - lightVariant.getRed(), 255 - lightVariant.getGreen(), 255 - lightVariant.getBlue()))
                    .build();
        }

    }

}
