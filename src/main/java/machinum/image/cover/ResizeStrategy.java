package machinum.image.cover;

import lombok.RequiredArgsConstructor;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface ResizeStrategy {

    static ResizeStrategy cropFocalPoint(double x, double y) {
        var localX = x == -1 ? 0.5 : x;
        var localY = y == -1 ? 0.5 : y;
        return new CropFocalPointStrategy(localX, localY);
    }

    static ResizeStrategy blurExpand(int blurRadius) {
        return new BlurExpandStrategy(blurRadius);
    }

    static ResizeStrategy padColor(Color color) {
        return new PadColorStrategy(color);
    }

    static ResizeStrategy replicate(int sampleSize) {
        return new ReplicateStrategy(sampleSize);
    }

    BufferedImage apply(BufferedImage image, double targetRatio);

    @RequiredArgsConstructor
    class CropFocalPointStrategy implements ResizeStrategy {

        private final double focalX, focalY;

        @Override
        public BufferedImage apply(BufferedImage image, double targetRatio) {
            int width = image.getWidth();
            int height = image.getHeight();
            double currentRatio = (double) width / height;

            if (Math.abs(currentRatio - targetRatio) < 0.01) {
                return image;
            }

            int newWidth, newHeight;
            if (currentRatio > targetRatio) {
                // Too wide, crop width
                newHeight = height;
                newWidth = (int) (height * targetRatio);
            } else {
                // Too tall, crop height
                newWidth = width;
                newHeight = (int) (width / targetRatio);
            }

            int x = (int) ((width - newWidth) * focalX);
            int y = (int) ((height - newHeight) * focalY);

            x = Math.max(0, Math.min(x, width - newWidth));
            y = Math.max(0, Math.min(y, height - newHeight));

            return image.getSubimage(x, y, newWidth, newHeight);
        }
    }

    @RequiredArgsConstructor
    class BlurExpandStrategy implements ResizeStrategy {

        private final int blurRadius;

        private static BufferedImage applyGaussianBlur(BufferedImage image, int radius) {
            if (radius <= 0) {
                return image;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            BufferedImage blurredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = blurredImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, width, height, null);
            g.dispose();

            float[] kernel = createGaussianKernel(radius);
            BufferedImage tempImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            // Horizontal blur
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    float sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                    float weightSum = 0;
                    for (int i = -radius; i <= radius; i++) {
                        int imageX = Math.min(Math.max(x + i, 0), width - 1);
                        int kernelIndex = i + radius;
                        int pixel = image.getRGB(imageX, y);
                        float weight = kernel[kernelIndex];
                        sumA += ((pixel >> 24) & 0xFF) * weight;
                        sumR += ((pixel >> 16) & 0xFF) * weight;
                        sumG += ((pixel >> 8) & 0xFF) * weight;
                        sumB += (pixel & 0xFF) * weight;
                        weightSum += weight;
                    }
                    int alpha = Math.round(sumA / weightSum);
                    int red = Math.round(sumR / weightSum);
                    int green = Math.round(sumG / weightSum);
                    int blue = Math.round(sumB / weightSum);
                    tempImage.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            }

            // Vertical blur
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    float sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                    float weightSum = 0;
                    for (int i = -radius; i <= radius; i++) {
                        int imageY = Math.min(Math.max(y + i, 0), height - 1);
                        int kernelIndex = i + radius;
                        int pixel = tempImage.getRGB(x, imageY);
                        float weight = kernel[kernelIndex];
                        sumA += ((pixel >> 24) & 0xFF) * weight;
                        sumR += ((pixel >> 16) & 0xFF) * weight;
                        sumG += ((pixel >> 8) & 0xFF) * weight;
                        sumB += (pixel & 0xFF) * weight;
                        weightSum += weight;
                    }
                    int alpha = Math.round(sumA / weightSum);
                    int red = Math.round(sumR / weightSum);
                    int green = Math.round(sumG / weightSum);
                    int blue = Math.round(sumB / weightSum);
                    blurredImage.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            }

            return blurredImage;
        }

        private static float[] createGaussianKernel(int radius) {
            int size = 2 * radius + 1;
            float[] kernel = new float[size];
            float sigma = radius / 3.0f;
            float twoSigmaSquare = 2.0f * sigma * sigma;
            float sigmaRoot = (float) Math.sqrt(twoSigmaSquare * Math.PI);
            float totalWeight = 0.0f;

            for (int i = -radius; i <= radius; i++) {
                float distance = i * i;
                int index = i + radius;
                kernel[index] = (float) Math.exp(-distance / twoSigmaSquare) / sigmaRoot;
                totalWeight += kernel[index];
            }

            for (int i = 0; i < size; i++) {
                kernel[i] /= totalWeight;
            }

            return kernel;
        }

        @Override
        public BufferedImage apply(BufferedImage image, double targetRatio) {
            int width = image.getWidth();
            int height = image.getHeight();
            double currentRatio = (double) width / height;

            if (Math.abs(currentRatio - targetRatio) < 0.01) {
                return image;
            }

            int newWidth, newHeight;
            if (currentRatio > targetRatio) {
                // Need to make taller
                newWidth = width;
                newHeight = (int) (width / targetRatio);
            } else {
                // Need to make wider
                newHeight = height;
                newWidth = (int) (height * targetRatio);
            }

            BufferedImage expanded = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = expanded.createGraphics();

            // Fill with blurred edges
            BufferedImage blurred = applyGaussianBlur(image, blurRadius);
            g.drawImage(blurred, 0, 0, newWidth, newHeight, null);

            // Draw original image centered
            int x = (newWidth - width) / 2;
            int y = (newHeight - height) / 2;
            g.drawImage(image, x, y, null);

            g.dispose();
            return expanded;
        }

    }

    @RequiredArgsConstructor
    class PadColorStrategy implements ResizeStrategy {

        private final Color padColor;

        @Override
        public BufferedImage apply(BufferedImage image, double targetRatio) {
            int width = image.getWidth();
            int height = image.getHeight();
            double currentRatio = (double) width / height;

            if (Math.abs(currentRatio - targetRatio) < 0.01) {
                return image;
            }

            int newWidth, newHeight;
            if (currentRatio > targetRatio) {
                newWidth = width;
                newHeight = (int) (width / targetRatio);
            } else {
                newHeight = height;
                newWidth = (int) (height * targetRatio);
            }

            BufferedImage padded = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = padded.createGraphics();

            g.setColor(padColor);
            g.fillRect(0, 0, newWidth, newHeight);

            int x = (newWidth - width) / 2;
            int y = (newHeight - height) / 2;
            g.drawImage(image, x, y, null);

            g.dispose();
            return padded;
        }
    }

    @RequiredArgsConstructor
    class ReplicateStrategy implements ResizeStrategy {

        private final int sampleSize;

        @Override
        public BufferedImage apply(BufferedImage image, double targetRatio) {
            int width = image.getWidth();
            int height = image.getHeight();
            double currentRatio = (double) width / height;

            if (Math.abs(currentRatio - targetRatio) < 0.01) {
                return image;
            }

            int newWidth, newHeight;
            if (currentRatio > targetRatio) {
                newWidth = width;
                newHeight = (int) (width / targetRatio);
            } else {
                newHeight = height;
                newWidth = (int) (height * targetRatio);
            }

            BufferedImage expanded = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = expanded.createGraphics();

            // Sample and replicate edges
            BufferedImage topSample = image.getSubimage(0, 0, width, Math.min(sampleSize, height));
            BufferedImage bottomSample = image.getSubimage(0, Math.max(0, height - sampleSize), width, Math.min(sampleSize, height));
            BufferedImage leftSample = image.getSubimage(0, 0, Math.min(sampleSize, width), height);
            BufferedImage rightSample = image.getSubimage(Math.max(0, width - sampleSize), 0, Math.min(sampleSize, width), height);

            // Fill expanded areas by tiling samples
            int extraWidth = newWidth - width;
            int extraHeight = newHeight - height;

            if (extraHeight > 0) {
                // Top expansion
                for (int y = 0; y < extraHeight / 2; y += sampleSize) {
                    g.drawImage(topSample, 0, y, null);
                }
                // Bottom expansion
                for (int y = newHeight - extraHeight / 2; y < newHeight; y += sampleSize) {
                    g.drawImage(bottomSample, 0, y, null);
                }
            }

            if (extraWidth > 0) {
                // Left expansion
                for (int x = 0; x < extraWidth / 2; x += sampleSize) {
                    g.drawImage(leftSample, x, 0, null);
                }
                // Right expansion
                for (int x = newWidth - extraWidth / 2; x < newWidth; x += sampleSize) {
                    g.drawImage(rightSample, x, 0, null);
                }
            }

            // Draw original image centered
            int x = (newWidth - width) / 2;
            int y = (newHeight - height) / 2;
            g.drawImage(image, x, y, null);

            g.dispose();
            return expanded;
        }
    }

}
