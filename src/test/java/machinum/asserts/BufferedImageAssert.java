package machinum.asserts;

import org.assertj.core.api.AbstractAssert;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.util.Arrays;
import java.util.Objects;

public class BufferedImageAssert extends AbstractAssert<BufferedImageAssert, BufferedImage> {

    protected BufferedImageAssert(BufferedImage actual) {
        super(actual, BufferedImageAssert.class);
    }

    public static BufferedImageAssert assertThat(BufferedImage actual) {
        return new BufferedImageAssert(actual);
    }

    /**
     * Verifies that the actual {@link BufferedImage} has the expected dimensions.
     *
     * @param expectedWidth  the expected width.
     * @param expectedHeight the expected height.
     * @return this assertion object.
     * @throws AssertionError if the actual image is {@code null}.
     * @throws AssertionError if the actual image's width or height is not equal to the expected ones.
     */
    public BufferedImageAssert hasDimensions(int expectedWidth, int expectedHeight) {
        isNotNull();
        if (actual.getWidth() != expectedWidth || actual.getHeight() != expectedHeight) {
            failWithMessage("Expected image to have dimensions %dx%d but was %dx%d",
                    expectedWidth, expectedHeight, actual.getWidth(), actual.getHeight());
        }
        return this;
    }

    /**
     * Verifies that the actual {@link BufferedImage} has the expected image type.
     *
     * @param expectedType the expected image type (e.g., {@link BufferedImage#TYPE_INT_RGB}).
     * @return this assertion object.
     * @throws AssertionError if the actual image is {@code null}.
     * @throws AssertionError if the actual image's type is not equal to the expected one.
     */
    public BufferedImageAssert hasType(int expectedType) {
        isNotNull();
        if (actual.getType() != expectedType) {
            failWithMessage("Expected image to have type %s but was %s",
                    getImageTypeName(expectedType), getImageTypeName(actual.getType()));
        }
        return this;
    }

    /**
     * Verifies that the actual {@link BufferedImage} has the same pixel data as the expected image.
     * This comparison checks dimensions, type, and then pixel-by-pixel data.
     *
     * @param expected the expected {@link BufferedImage}.
     * @return this assertion object.
     * @throws NullPointerException if the expected image is {@code null}.
     * @throws AssertionError       if the actual image is {@code null}.
     * @throws AssertionError       if the images do not have the same dimensions or type.
     * @throws AssertionError       if the pixel data of the images are not identical.
     */
    public BufferedImageAssert hasSameDataAs(BufferedImage expected) {
        isNotNull();
        Objects.requireNonNull(expected, "The expected BufferedImage cannot be null.");

        if (actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight()) {
            failWithMessage("Expected image to have dimensions %dx%d but was %dx%d",
                    expected.getWidth(), expected.getHeight(), actual.getWidth(), actual.getHeight());
        }

        if (actual.getType() != expected.getType()) {
            failWithMessage("Expected image to have type %s but was %s",
                    getImageTypeName(expected.getType()), getImageTypeName(actual.getType()));
        }

        // Efficiently compare pixel data
        DataBuffer actualDataBuffer = actual.getRaster().getDataBuffer();
        DataBuffer expectedDataBuffer = expected.getRaster().getDataBuffer();

        if (actualDataBuffer.getDataType() != expectedDataBuffer.getDataType() || actualDataBuffer.getSize() != expectedDataBuffer.getSize()) {
            // Fallback to slower per-pixel comparison if buffer types or sizes differ,
            // though type check above should catch most inconsistencies.
            // This path is less likely if types are the same.
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    if (actual.getRGB(x, y) != expected.getRGB(x, y)) {
                        failWithMessage("Images differ at pixel [%d, %d]. Expected ARGB: %08X, Actual ARGB: %08X",
                                x, y, expected.getRGB(x, y), actual.getRGB(x, y));
                    }
                }
            }
            return this;
        }


        // Compare underlying arrays if possible for performance
        // This works for common BufferedImage types like TYPE_INT_RGB, TYPE_INT_ARGB, etc.
        // For byte-based buffers:
        if (actualDataBuffer.getDataType() == DataBuffer.TYPE_BYTE) {
            byte[] actualPixels = ((java.awt.image.DataBufferByte) actualDataBuffer).getData();
            byte[] expectedPixels = ((java.awt.image.DataBufferByte) expectedDataBuffer).getData();
            if (!Arrays.equals(actualPixels, expectedPixels)) {
                failWithMessage("Pixel data differs. Image data arrays are not equal.");
            }
        }
        // For int-based buffers:
        else if (actualDataBuffer.getDataType() == DataBuffer.TYPE_INT) {
            int[] actualPixels = ((java.awt.image.DataBufferInt) actualDataBuffer).getData();
            int[] expectedPixels = ((java.awt.image.DataBufferInt) expectedDataBuffer).getData();
            if (!Arrays.equals(actualPixels, expectedPixels)) {
                failWithMessage("Pixel data differs. Image data arrays are not equal.");
            }
        }
        // For short/ushort-based buffers:
        else if (actualDataBuffer.getDataType() == DataBuffer.TYPE_USHORT) {
            short[] actualPixels = ((java.awt.image.DataBufferUShort) actualDataBuffer).getData();
            short[] expectedPixels = ((java.awt.image.DataBufferUShort) expectedDataBuffer).getData();
            if (!Arrays.equals(actualPixels, expectedPixels)) {
                failWithMessage("Pixel data differs. Image data arrays are not equal.");
            }
        } else if (actualDataBuffer.getDataType() == DataBuffer.TYPE_SHORT) {
            short[] actualPixels = ((java.awt.image.DataBufferShort) actualDataBuffer).getData();
            short[] expectedPixels = ((java.awt.image.DataBufferShort) expectedDataBuffer).getData();
            if (!Arrays.equals(actualPixels, expectedPixels)) {
                failWithMessage("Pixel data differs. Image data arrays are not equal.");
            }
        }
        // Fallback for other or unknown buffer types (less common for standard images)
        else {
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    if (actual.getRGB(x, y) != expected.getRGB(x, y)) {
                        failWithMessage("Images differ at pixel [%d, %d]. Expected ARGB: %08X, Actual ARGB: %08X",
                                x, y, expected.getRGB(x, y), actual.getRGB(x, y));
                    }
                }
            }
        }
        return this;
    }

    /**
     * Verifies that the actual {@link BufferedImage} has the same pixel data as the expected image,
     * within a given color tolerance for each ARGB component.
     *
     * @param expected  the expected {@link BufferedImage}.
     * @param tolerance the maximum allowed difference for each Alpha, Red, Green, Blue component (0-255).
     * @return this assertion object.
     * @throws NullPointerException     if the expected image is {@code null}.
     * @throws IllegalArgumentException if tolerance is negative or greater than 255.
     * @throws AssertionError           if the actual image is {@code null}.
     * @throws AssertionError           if the images do not have the same dimensions.
     * @throws AssertionError           if any pixel's ARGB components differ by more than the tolerance.
     */
    public BufferedImageAssert hasSameDataAs(BufferedImage expected, int tolerance) {
        isNotNull();
        Objects.requireNonNull(expected, "The expected BufferedImage cannot be null.");
        if (tolerance < 0 || tolerance > 255) {
            throw new IllegalArgumentException("Tolerance must be between 0 and 255.");
        }

        hasDimensions(expected.getWidth(), expected.getHeight()); // Leverage existing dimension check

        // No need to check type strictly here as per-pixel comparison handles different representations
        // as long as they resolve to comparable ARGB values.

        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                int actualRGB = actual.getRGB(x, y);
                int expectedRGB = expected.getRGB(x, y);

                if (actualRGB == expectedRGB) continue; // Quick path for identical pixels

                int actualAlpha = (actualRGB >> 24) & 0xFF;
                int actualRed = (actualRGB >> 16) & 0xFF;
                int actualGreen = (actualRGB >> 8) & 0xFF;
                int actualBlue = actualRGB & 0xFF;

                int expectedAlpha = (expectedRGB >> 24) & 0xFF;
                int expectedRed = (expectedRGB >> 16) & 0xFF;
                int expectedGreen = (expectedRGB >> 8) & 0xFF;
                int expectedBlue = expectedRGB & 0xFF;

                if (Math.abs(actualAlpha - expectedAlpha) > tolerance ||
                        Math.abs(actualRed - expectedRed) > tolerance ||
                        Math.abs(actualGreen - expectedGreen) > tolerance ||
                        Math.abs(actualBlue - expectedBlue) > tolerance) {
                    failWithMessage("Images differ at pixel [%d, %d] with tolerance %d.\n" +
                                    "Expected ARGB: A=%d, R=%d, G=%d, B=%d (%08X)\n" +
                                    "Actual   ARGB: A=%d, R=%d, G=%d, B=%d (%08X)",
                            x, y, tolerance,
                            expectedAlpha, expectedRed, expectedGreen, expectedBlue, expectedRGB,
                            actualAlpha, actualRed, actualGreen, actualBlue, actualRGB);
                }
            }
        }
        return this;
    }


    private String getImageTypeName(int type) {
        return switch (type) {
            case BufferedImage.TYPE_INT_RGB -> "TYPE_INT_RGB";
            case BufferedImage.TYPE_INT_ARGB -> "TYPE_INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE -> "TYPE_INT_ARGB_PRE";
            case BufferedImage.TYPE_INT_BGR -> "TYPE_INT_BGR";
            case BufferedImage.TYPE_3BYTE_BGR -> "TYPE_3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR -> "TYPE_4BYTE_ABGR";
            case BufferedImage.TYPE_4BYTE_ABGR_PRE -> "TYPE_4BYTE_ABGR_PRE";
            case BufferedImage.TYPE_USHORT_565_RGB -> "TYPE_USHORT_565_RGB";
            case BufferedImage.TYPE_USHORT_555_RGB -> "TYPE_USHORT_555_RGB";
            case BufferedImage.TYPE_BYTE_GRAY -> "TYPE_BYTE_GRAY";
            case BufferedImage.TYPE_USHORT_GRAY -> "TYPE_USHORT_GRAY";
            case BufferedImage.TYPE_BYTE_BINARY -> "TYPE_BYTE_BINARY";
            case BufferedImage.TYPE_BYTE_INDEXED -> "TYPE_BYTE_INDEXED";
            case BufferedImage.TYPE_CUSTOM -> "TYPE_CUSTOM";
            default -> "Unknown type (" + type + ")";
        };
    }

}
