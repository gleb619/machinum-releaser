package machinum.image.cover;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import machinum.image.cover.Spine.SlotTextAlign;

import java.awt.*;
import java.awt.image.BufferedImage;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FontSizeCalculator {

    public static final int MAX_FONT_SIZE = 80;
    public static final int MIN_FONT_SIZE = 28;

    public static Font getBaseFont() {
        return getBaseFont(28);
    }

    public static Font getBaseFont(int fonSize) {
        return new Font("Arial", Font.BOLD, fonSize);
    }

    public static Font calculateOptimalFont(String text, int maxWidth, int maxHeight,
                                            Font baseFont, boolean isTitle) {
        if (text == null || text.trim().isEmpty()) {
            return baseFont;
        }

        // Base font sizes
        int maxFontSize = isTitle ? 144 : 72;
        int minFontSize = isTitle ? 48 : 24;

        // Text length based sizing
        int textLength = text.trim().length();
        int baseFontSize = calculateBaseFontSize(textLength, isTitle);

        // Create test graphics for measurement
        BufferedImage testImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D testG2d = testImg.createGraphics();

        int fontSize = Math.min(baseFontSize, maxFontSize);
        Font testFont = new Font(baseFont.getName(), baseFont.getStyle(), fontSize);

        // Adjust font size to fit within bounds
        while (fontSize > minFontSize) {
            testG2d.setFont(testFont);
            FontMetrics fm = testG2d.getFontMetrics();

            if (fitsWithinBounds(text, fm, maxWidth, maxHeight)) {
                break;
            }

            fontSize -= 2;
            testFont = new Font(baseFont.getName(), baseFont.getStyle(), fontSize);
        }

        testG2d.dispose();
        return testFont;
    }

    public static Font calculateFont(String text, int width, int height,
                                     Font baseFont, SlotTextAlign alignment) {
        if (text == null || text.trim().isEmpty()) {
            return baseFont;
        }

        int textLength = text.trim().length();
        int maxFontSize = Math.min(width / 2, MAX_FONT_SIZE);

        // For vertical text, consider height constraint
        int fontSize;
        if (alignment == SlotTextAlign.VERTICALLY) {
            fontSize = (int) Math.min(maxFontSize, height * 0.061);
        } else {
            fontSize = calculateBaseFontSize(textLength, false);
            fontSize = Math.max(MIN_FONT_SIZE, Math.min(fontSize, maxFontSize));
        }

        return new Font(baseFont.getName(), baseFont.getStyle(), fontSize);
    }

    private static int calculateBaseFontSize(int textLength, boolean isTitle) {
        if (textLength <= 10) {
            return isTitle ? 128 : 64;
        } else if (textLength <= 20) {
            return isTitle ? 104 : 52;
        } else if (textLength <= 30) {
            return isTitle ? 88 : 44;
        } else if (textLength <= 50) {
            return isTitle ? 72 : 36;
        } else {
            return isTitle ? 56 : 28;
        }
    }

    private static boolean fitsWithinBounds(String text, FontMetrics fm, int maxWidth, int maxHeight) {
        String[] words = text.split(" ");
        int lineHeight = fm.getHeight();
        int currentLineWidth = 0;
        int totalHeight = lineHeight;

        for (String word : words) {
            int wordWidth = fm.stringWidth(word + " ");

            if (currentLineWidth + wordWidth > maxWidth) {
                totalHeight += lineHeight;
                currentLineWidth = wordWidth;
            } else {
                currentLineWidth += wordWidth;
            }
        }

        return totalHeight <= maxHeight;
    }

}
