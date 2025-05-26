package machinum.image;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.image.CoverService.SpineSlot.SlotGravity;
import machinum.image.CoverService.SpineSlot.TextAlign;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

@Slf4j
public class CoverService {

    @SneakyThrows
    public Image generate(Image originImage) {
        File originWithTriangles = TriangleWrapper.addTriangleEffect(originImage);

        BufferedImage origin = Loader.load(originImage);

        BufferedImage inspiration = Loader.mirror(originWithTriangles);
        ColorPalette palette = ColorSampler.fromImage(inspiration).extractDominant();

        var cover = BookCover.builder()
                .width(origin.getWidth())
                .height(origin.getHeight())
                .background(Background.gradientFade(inspiration, Background.GradientDirection.RIGHT_TO_LEFT))
                .spine(Spine.left(s -> s
                                .addTextSlot(b -> b
                                        .type(SpineSlot.Type.TEXT)
                                        .text("1")
                                        .slotGravity(SlotGravity.TOP))
                                .addTextSlot(b -> b
                                        .type(SpineSlot.Type.TEXT)
                                        //TODO change name of picture/cover
                                        .text("[0001] Вынужден взойти на трон после трансмиграции")
                                        .textAlign(TextAlign.VERTICALLY))
                                .addQRSlot(Loader.load("~/Downloads/qr.png"))
                        )
                        .width(Math.max(40, Math.round((float) origin.getWidth() / 6)))
                        .color(palette.getDarkVariant())
                        .withShadow(true)
                        .withHighlight(true)
                        .build())
                .baseColor(palette.getDominant())
                .spineColor(palette.getDarkVariant())
                .build();

        cover.renderToFile(File.createTempFile("machinum", "_cover.jpg").getAbsolutePath(), BookCover.ImageFormat.JPEG);

        return Image.builder()
                .name(originImage.getName() + "-cover")
                .contentType(originImage.getContentType())
                .data(cover.renderToBytes())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public List<BookCover> createEditionSeries(BookCover template, int editionCount) throws IOException {
        BufferedImage inspiration = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ColorPalette palette = ColorSampler.fromImage(inspiration).extractDominant();

        return BookCover.generateEditions(template, palette, editionCount);
    }

    /* ============= */

    /**
     * Doesn't work, we need to use another library
     *
     * @param text
     * @param size
     * @return
     */
    @Deprecated
    public BufferedImage generateQRCode(String text, int size) {
        // Simple QR code placeholder - in real implementation use ZXing library
        BufferedImage qr = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = qr.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, size, size);
        g2d.setColor(Color.BLACK);

        // Draw simple pattern to simulate QR code
        int blockSize = size / 10;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if ((i + j) % 2 == 0) {
                    g2d.fillRect(i * blockSize, j * blockSize, blockSize, blockSize);
                }
            }
        }

        g2d.dispose();
        return qr;
    }

    public enum TextPlacement {

        BODY, SPINE

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Loader {

        @SneakyThrows
        public static BufferedImage load(String path) {
            return ImageIO.read(new FileInputStream(path));
        }

        @SneakyThrows
        public static BufferedImage load(Path path) {
            return ImageIO.read(new FileInputStream(path.toFile()));
        }

        @SneakyThrows
        public static BufferedImage load(Image originImage) {
            return ImageIO.read(new ByteArrayInputStream(originImage.getData()));
        }

        public static BufferedImage mirror(Image originImage) {
            var original = load(originImage);
            return mirror(original);
        }

        public static BufferedImage mirror(File file) {
            var original = load(file.toPath());
            return mirror(original);
        }

        public static BufferedImage mirror(BufferedImage original) {
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

    @Data
    @Builder
    public static class ColorPalette {

        private Color dominant;
        private Color darkVariant;
        private Color lightVariant;
        private List<Color> variants;

        public Color getVariant(int index) {
            return variants.get(index % variants.size());
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FontSizeCalculator {

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

        public static Font calculateSpineFont(String text, int spineWidth, int availableHeight,
                                              Font baseFont, TextAlign alignment) {
            if (text == null || text.trim().isEmpty()) {
                return baseFont;
            }

            int textLength = text.trim().length();
            int maxFontSize = Math.min(spineWidth - 4, 40);
            int minFontSize = 16;

            // For vertical text, consider height constraint
            if (alignment == TextAlign.VERTICALLY) {
                maxFontSize = Math.min(maxFontSize, availableHeight / Math.max(textLength, 1));
            }

            int fontSize = calculateBaseFontSize(textLength, false);
            fontSize = Math.max(minFontSize, Math.min(fontSize, maxFontSize));

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

    @Data
    @Builder
    public static class Background {

        private Type type;
        private BufferedImage sourceImage;
        private Color color;
        private int blurRadius;
        private float opacity;
        private GradientDirection gradientDirection;

        public static Background expandFrom(BufferedImage image) {
            return Background.builder()
                    .type(Type.BLURRED_EXPANSION)
                    .sourceImage(image)
                    .blurRadius(15)
                    .build();
        }

        public static Background colorWash(Color color, float opacity) {
            return Background.builder()
                    .type(Type.COLOR_WASH)
                    .color(color)
                    .opacity(opacity)
                    .build();
        }

        public static Background gradientFade(BufferedImage image, GradientDirection direction) {
            return Background.builder()
                    .type(Type.GRADIENT_FADE)
                    .sourceImage(image)
                    .gradientDirection(direction)
                    .build();
        }

        public Background blurRadius(int radius) {
            this.blurRadius = radius;
            return this;
        }

        public enum Type {
            SOLID_COLOR, BLURRED_EXPANSION, COLOR_WASH, GRADIENT_FADE
        }

        public enum GradientDirection {
            LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, RADIAL
        }
    }

    @Data
    @Builder(toBuilder = true)
    public static class Spine {

        private SpinePosition position;
        private int width;
        private Color color;
        private boolean withShadow;
        private boolean withHighlight;
        @Builder.Default
        private List<SpineSlot> slots = new ArrayList<>();

        public static SpineBuilder left(Function<Spine, Spine> customizer) {
            return customizer.apply(Spine.builder().position(SpinePosition.LEFT).build())
                    .toBuilder();
        }

        public static SpineBuilder right() {
            return Spine.builder().position(SpinePosition.RIGHT);
        }

        public Spine width(int width) {
            this.width = width;
            return this;
        }

        public Spine addTextSlot(Function<SpineSlot.SpineSlotBuilder, SpineSlot.SpineSlotBuilder> creator) {
            SpineSlot slot = creator.apply(SpineSlot.builder()).build();
            if (slot.getPosition() == -1) {
                slot.setPosition(this.slots.size());
            }
            this.slots.add(slot);
            return this;
        }

        public Spine addQRSlot(BufferedImage qrImage) {
            this.slots.add(SpineSlot.qrCode(qrImage, this.slots.size()));
            return this;
        }

        public enum SpinePosition {
            LEFT, RIGHT, NONE
        }

    }

    @Data
    @Builder
    public static class SpineSlot {
        @Builder.Default
        private Type type = Type.TEXT;
        private String text;
        private BufferedImage image;
        private Font font;
        private Color textColor;
        @Builder.Default
        private TextAlign textAlign = TextAlign.HORIZONTALLY;
        @Builder.Default
        private SlotGravity slotGravity = SlotGravity.NONE;
        @Builder.Default
        private int position = -1; // 0=top, 1=middle, 2=bottom

        public static SpineSlot text(String text, int position) {
            return SpineSlot.builder()
                    .type(Type.TEXT)
                    .text(text)
                    .position(position)
                    .textAlign(TextAlign.VERTICALLY)
                    .build();
        }

        public static SpineSlot image(BufferedImage image, int position) {
            return SpineSlot.builder()
                    .type(Type.IMAGE)
                    .image(image)
                    .position(position)
                    .build();
        }

        public static SpineSlot qrCode(BufferedImage qrImage, int position) {
            return SpineSlot.builder()
                    .type(Type.QR_CODE)
                    .image(qrImage)
                    .position(position)
                    .slotGravity(position > 1 ? SlotGravity.BOTTOM : SlotGravity.NONE)
                    .build();
        }

        public enum Type {
            TEXT, IMAGE, QR_CODE
        }

        public enum TextAlign {
            HORIZONTALLY, VERTICALLY
        }

        public enum SlotGravity {
            NONE, TOP, BOTTOM
        }

    }

    @Data
    @Builder(toBuilder = true)
    @Slf4j
    public static class BookCover {
        @Builder.Default
        private int width = 1200;
        @Builder.Default
        private int height = 1800;
        @Builder.Default
        private String title = "";
        @Builder.Default
        private String author = "";
        private Background background;
        private Spine spine;
        private Color baseColor;
        private Color spineColor;
        private Color accentColor;
        @Builder.Default
        private int margin = 30;
        private Font titleFont;
        private Font authorFont;
        @Builder.Default
        private boolean dynamicFontSizing = true;
        @Builder.Default
        private int maxTitleFontSize = 144;
        @Builder.Default
        private int maxAuthorFontSize = 72;
        @Builder.Default
        private int minTitleFontSize = 40;
        @Builder.Default
        private int minAuthorFontSize = 20;
        @Builder.Default
        private TextAlign titleTextAlign = TextAlign.HORIZONTALLY;
        @Builder.Default
        private TextAlign authorTextAlign = TextAlign.VERTICALLY;
        @Builder.Default
        private TextPlacement titlePlacement = TextPlacement.SPINE;
        @Builder.Default
        private TextPlacement authorPlacement = TextPlacement.SPINE;
        private Color titleTextColor;
        private Color authorTextColor;

        public static List<BookCover> generateEditions(BookCover template, ColorPalette palette, int count) {
            return IntStream.range(1, count + 1)
                    .mapToObj(i -> template.toBuilder()
                            .title("Edition " + i)
                            .accentColor(palette.getVariant(i - 1))
                            .build())
                    .toList();
        }

        public BufferedImage render() {
            BufferedImage cover = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = cover.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Render background
            renderBackground(g2d);

            // Render spine
            if (spine != null && spine.getPosition() != Spine.SpinePosition.NONE) {
                renderSpine(g2d);
            }

            // Render text
            renderText(g2d);

            g2d.dispose();
            return cover;
        }

        private void renderBackground(Graphics2D g2d) {
            if (background == null) {
                g2d.setColor(baseColor != null ? baseColor : Color.WHITE);
                g2d.fillRect(0, 0, width, height);
                return;
            }

            switch (background.getType()) {
                case SOLID_COLOR -> {
                    g2d.setColor(background.getColor());
                    g2d.fillRect(0, 0, width, height);
                }
                case BLURRED_EXPANSION -> renderBlurredBackground(g2d);
                case COLOR_WASH -> renderColorWash(g2d);
                case GRADIENT_FADE -> renderGradientFade(g2d);
            }
        }

        private void renderBlurredBackground(Graphics2D g2d) {
            if (background.getSourceImage() == null) return;

            BufferedImage scaled = scaleImage(background.getSourceImage(), width, height);
            BufferedImage blurred = applyGaussianBlur(scaled, background.getBlurRadius());
            g2d.drawImage(blurred, 0, 0, null);
        }

        private void renderColorWash(Graphics2D g2d) {
            g2d.setColor(background.getColor());
            g2d.fillRect(0, 0, width, height);

            if (background.getSourceImage() != null) {
                Composite original = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, background.getOpacity()));
                BufferedImage scaled = scaleImage(background.getSourceImage(), width, height);
                g2d.drawImage(scaled, 0, 0, null);
                g2d.setComposite(original);
            }
        }

        private void renderGradientFade(Graphics2D g2d) {
            if (background.getSourceImage() == null || background.getGradientDirection() == null) return;

            BufferedImage scaled = scaleImage(background.getSourceImage(), width, height);
            g2d.drawImage(scaled, 0, 0, null);

            GradientPaint gradient = switch (background.getGradientDirection()) {
                case LEFT_TO_RIGHT -> new GradientPaint(0, 0, new Color(0, 0, 0, 0), width, 0, new Color(0, 0, 0, 180));
                case RIGHT_TO_LEFT -> new GradientPaint(width, 0, new Color(0, 0, 0, 0), 0, 0, new Color(0, 0, 0, 180));
                case TOP_TO_BOTTOM ->
                        new GradientPaint(0, 0, new Color(0, 0, 0, 0), 0, height, new Color(0, 0, 0, 180));
                case RADIAL ->
                        new GradientPaint(width / 2f, height / 2f, new Color(0, 0, 0, 0), width, height, new Color(0, 0, 0, 180));
            };

            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, width, height);
        }

        private void renderSpine(Graphics2D g2d) {
            int spineX = spine.getPosition() == Spine.SpinePosition.LEFT ? 0 : width - spine.getWidth();

            // Base spine color
            g2d.setColor(spine.getColor() != null ? spine.getColor() : (spineColor != null ? spineColor : Color.DARK_GRAY));
            g2d.fillRect(spineX, 0, spine.getWidth(), height);

            // Shadow effect
            if (spine.isWithShadow()) {
                LinearGradientPaint shadowGradient = new LinearGradientPaint(
                        spineX, 0, spineX + spine.getWidth(), 0,
                        new float[]{0.0f, 1.0f},
                        new Color[]{new Color(0, 0, 0, 80), new Color(0, 0, 0, 0)}
                );
                g2d.setPaint(shadowGradient);
                g2d.fillRect(spineX, 0, spine.getWidth(), height);
            }

            // Highlight effect
            if (spine.isWithHighlight()) {
                g2d.setColor(new Color(255, 255, 255, 40));
                int highlightX = spine.getPosition() == Spine.SpinePosition.LEFT ? spineX + spine.getWidth() - 1 : spineX;
                g2d.fillRect(highlightX, 0, 1, height);
            }

            // Render spine slots
            renderSpineSlots(g2d, spineX);
        }

        private void renderSpineSlots(Graphics2D g2d, int spineX) {
            if (spine.getSlots().isEmpty()) return;

            int slotHeight = height / 3;

            for (SpineSlot slot : spine.getSlots()) {
                int slotY = slot.getPosition() * slotHeight;

                // Calculate dynamic font for spine slots if not set
                if (slot.getType() == SpineSlot.Type.TEXT && slot.getFont() == null && dynamicFontSizing) {
                    Font baseFont = new Font("Arial", Font.BOLD, 28);
                    Font dynamicFont = FontSizeCalculator.calculateSpineFont(
                            slot.getText(), spine.getWidth(), slotHeight, baseFont, slot.getTextAlign()
                    );
                    slot.setFont(dynamicFont);
                }

                switch (slot.getType()) {
                    case TEXT -> renderSpineText(g2d, slot, spineX, slotY, slotHeight);
                    case IMAGE, QR_CODE -> renderSpineImage(g2d, slot, spineX, slotY, slotHeight);
                }
            }
        }

        private void renderSpineText(Graphics2D g2d, SpineSlot slot, int spineX, int slotY, int slotHeight) {
            if (slot.getText() == null) return;

            Font font = slot.getFont() != null ? slot.getFont() : new Font("Arial", Font.BOLD, 28);
            g2d.setFont(font);

            // Calculate text color based on spine background
            Color textColor = slot.getTextColor();
            if (textColor == null) {
                textColor = ColorSampler.calculateTextColor(spine.getColor(), TextPlacement.SPINE, true);
            }
            g2d.setColor(textColor);

            FontMetrics fm = g2d.getFontMetrics();

            if (slot.getTextAlign() == TextAlign.VERTICALLY) {
                renderVerticalText(g2d, slot.getText(), spineX + spine.getWidth() / 2, slotY + slotHeight / 2, fm);
            } else {
                renderHorizontalSpineText(g2d, slot, spineX, slotY, slotHeight, fm);
            }
        }

        private void renderSpineImage(Graphics2D g2d, SpineSlot slot, int spineX, int slotY, int slotHeight) {
            if (slot.getImage() == null) return;

            int imageSize = Math.min(spine.getWidth() - 4, slotHeight - 4);
            int imageX = spineX + (spine.getWidth() - imageSize) / 2;
            int imageY = slotY + (slotHeight - imageSize) / 2;

            imageY = switch (slot.getSlotGravity()) {
                case TOP -> imageY - slotHeight / 4;
                case BOTTOM -> imageY + slotHeight / 4;
                default -> imageY;
            };

            g2d.drawImage(slot.getImage(), imageX, imageY, imageSize, imageSize, null);
        }

        private void renderVerticalText(Graphics2D g2d, String text, int centerX, int centerY, FontMetrics fm) {
            AffineTransform originalTransform = g2d.getTransform();

            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            g2d.translate(centerX, centerY);
            g2d.rotate(-Math.PI / 2);
            g2d.drawString(text, -textWidth / 2, textHeight / 4);

            g2d.setTransform(originalTransform);
        }

        private void renderHorizontalSpineText(Graphics2D g2d, SpineSlot slot, int spineX, int slotY, int slotHeight, FontMetrics fm) {
            int textWidth = fm.stringWidth(slot.getText());
            int textX = spineX + (spine.getWidth() - textWidth) / 2;
            int textY = slotY + slotHeight / 2 + fm.getAscent() / 2;
            textY = switch (slot.getSlotGravity()) {
                case TOP -> textY - slotHeight / 4;
                case BOTTOM -> textY + slotHeight / 4;
                default -> textY;
            };

            g2d.drawString(slot.getText(), textX, textY);
        }

        private void renderText(Graphics2D g2d) {
            int textAreaX = spine != null && spine.getPosition() == Spine.SpinePosition.LEFT ? spine.getWidth() + margin : margin;
            int textAreaWidth = width - margin * 2 - (spine != null ? spine.getWidth() : 0);
            int maxTextHeight = height / 2 - margin;

            // Title
            if (title != null) {
                Font font = titleFont;

                // Calculate dynamic font size if enabled and no custom font is set
                if (dynamicFontSizing && font == null) {
                    Font baseFont = new Font("Arial", Font.BOLD, 72);
                    font = FontSizeCalculator.calculateOptimalFont(title, textAreaWidth, maxTextHeight, baseFont, true);
                } else if (font == null) {
                    font = new Font("Arial", Font.BOLD, 72);
                }

                g2d.setFont(font);

                // Calculate title color
                Color color = titleTextColor;
                if (color == null) {
                    TextPlacement placement = titlePlacement != null ? titlePlacement : TextPlacement.BODY;
                    color = ColorSampler.calculateTextColor(baseColor != null ? baseColor : Color.WHITE, placement, true);
                }
                g2d.setColor(color);

                FontMetrics fm = g2d.getFontMetrics();
                int titleY = height / 3;

                TextAlign align = titleTextAlign != null ? titleTextAlign : TextAlign.HORIZONTALLY;
                if (align == TextAlign.HORIZONTALLY) {
                    drawWrappedText(g2d, title, textAreaX, titleY, textAreaWidth, fm.getHeight());
                } else {
                    renderVerticalText(g2d, title, textAreaX + textAreaWidth / 2, titleY, fm);
                }
            }

            // Author
            if (author != null) {
                Font font = authorFont;

                // Calculate dynamic font size if enabled and no custom font is set
                if (dynamicFontSizing && font == null) {
                    Font baseFont = new Font("Arial", Font.PLAIN, 48);
                    font = FontSizeCalculator.calculateOptimalFont(author, textAreaWidth, maxTextHeight / 2, baseFont, false);
                } else if (font == null) {
                    font = new Font("Arial", Font.PLAIN, 48);
                }

                g2d.setFont(font);

                // Calculate author color
                Color color = authorTextColor;
                if (color == null) {
                    TextPlacement placement = authorPlacement != null ? authorPlacement : TextPlacement.BODY;
                    color = ColorSampler.calculateTextColor(baseColor != null ? baseColor : Color.WHITE, placement, false);
                }
                g2d.setColor(color);

                FontMetrics fm = g2d.getFontMetrics();
                int authorY = height - height / 4;

                TextAlign align = authorTextAlign != null ? authorTextAlign : TextAlign.HORIZONTALLY;
                if (align == TextAlign.HORIZONTALLY) {
                    drawWrappedText(g2d, author, textAreaX, authorY, textAreaWidth, fm.getHeight());
                } else {
                    renderVerticalText(g2d, author, textAreaX + textAreaWidth / 2, authorY, fm);
                }
            }
        }

        private void drawWrappedText(Graphics2D g2d, String text, int x, int y, int maxWidth, int lineHeight) {
            String[] words = text.split(" ");
            StringBuilder line = new StringBuilder();
            int currentY = y;
            FontMetrics fm = g2d.getFontMetrics();

            for (String word : words) {
                String testLine = line.isEmpty() ? word : line + " " + word;

                if (fm.stringWidth(testLine) > maxWidth && !line.isEmpty()) {
                    // Center the text horizontally
                    int lineWidth = fm.stringWidth(line.toString());
                    int centeredX = x + (maxWidth - lineWidth) / 2;
                    g2d.drawString(line.toString(), centeredX, currentY);

                    line = new StringBuilder(word);
                    currentY += lineHeight;
                } else {
                    line = new StringBuilder(testLine);
                }
            }

            if (!line.isEmpty()) {
                int lineWidth = fm.stringWidth(line.toString());
                int centeredX = x + (maxWidth - lineWidth) / 2;
                g2d.drawString(line.toString(), centeredX, currentY);
            }
        }

        private BufferedImage scaleImage(BufferedImage original, int targetWidth, int targetHeight) {
            double scaleX = (double) targetWidth / original.getWidth();
            double scaleY = (double) targetHeight / original.getHeight();
            double scale = Math.max(scaleX, scaleY);

            int scaledWidth = (int) (original.getWidth() * scale);
            int scaledHeight = (int) (original.getHeight() * scale);

            BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
            g2d.dispose();

            // Center crop
            int x = (scaledWidth - targetWidth) / 2;
            int y = (scaledHeight - targetHeight) / 2;
            return scaled.getSubimage(Math.max(0, x), Math.max(0, y), targetWidth, targetHeight);
        }

        private BufferedImage applyGaussianBlur(BufferedImage image, int radius) {
            if (radius <= 0) return image;

            int size = radius * 2 + 1;
            float[] matrix = new float[size * size];
            float sigma = radius / 3.0f;
            float sum = 0;

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int x = i - radius;
                    int y = j - radius;
                    matrix[i * size + j] = (float) Math.exp(-(x * x + y * y) / (2 * sigma * sigma));
                    sum += matrix[i * size + j];
                }
            }

            for (int i = 0; i < matrix.length; i++) {
                matrix[i] /= sum;
            }

            Kernel kernel = new Kernel(size, size, matrix);
            ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            return op.filter(image, null);
        }

        public void renderToFile(String filename, ImageFormat format) throws IOException {
            BufferedImage rendered = render();
            String formatName = format == ImageFormat.PNG ? "PNG" : "JPEG";
            ImageIO.write(rendered, formatName, new File(filename));
        }

        @SneakyThrows
        public byte[] renderToBytes() {
            BufferedImage rendered = render();
            var raster = rendered.getRaster();
            if (raster.getDataBuffer() instanceof DataBufferByte dbb) {
                return dbb.getData();
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(rendered, "png", baos);
                return baos.toByteArray();
            }
        }

        public enum ImageFormat {
            PNG, JPEG
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public class ColorSampler {

        public static ColorSamplerBuilder fromImage(BufferedImage image) {
            return new ColorSamplerBuilder(image);
        }

        public static Color calculateTextColor(Color backgroundColor, TextPlacement placement) {
            return calculateTextColor(backgroundColor, placement, true);
        }

        public static Color calculateTextColor(Color backgroundColor, TextPlacement placement, boolean isTitle) {
            double luminance = calculateLuminance(backgroundColor);

            // Different contrast requirements for spine vs body
            double contrastThreshold = switch (placement) {
                case SPINE -> 4.5; // Higher contrast needed for spine text
                case BODY -> 3.0;  // Lower contrast acceptable for body text
            };

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

        public static Color calculateOptimalTextColor(BufferedImage backgroundRegion, TextPlacement placement, boolean isTitle) {
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
            return calculateTextColor(averageColor, placement, isTitle);
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

        public static class ColorSamplerBuilder {

            private final BufferedImage image;

            private ColorSamplerBuilder(BufferedImage image) {
                this.image = image;
            }

            public ColorPalette extractDominant() {
                Map<Integer, Integer> colorCount = new HashMap<>();

                for (int y = 0; y < image.getHeight(); y += 2) {
                    for (int x = 0; x < image.getWidth(); x += 2) {
                        int rgb = image.getRGB(x, y);
                        colorCount.merge(rgb, 1, Integer::sum);
                    }
                }

                int dominantRgb = colorCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(0x808080);

                Color dominant = new Color(dominantRgb);

                return ColorPalette.builder()
                        .dominant(dominant)
                        .darkVariant(darken(dominant, 0.3f))
                        .lightVariant(lighten(dominant, 0.3f))
                        .variants(generateVariants(dominant))
                        .build();
            }

            public ColorPalette withBrightnessVariants(int count) {
                return extractDominant();
            }

            private Color darken(Color color, float factor) {
                return new Color(
                        Math.max(0, (int) (color.getRed() * (1 - factor))),
                        Math.max(0, (int) (color.getGreen() * (1 - factor))),
                        Math.max(0, (int) (color.getBlue() * (1 - factor)))
                );
            }

            private Color lighten(Color color, float factor) {
                return new Color(
                        Math.min(255, (int) (color.getRed() + (255 - color.getRed()) * factor)),
                        Math.min(255, (int) (color.getGreen() + (255 - color.getGreen()) * factor)),
                        Math.min(255, (int) (color.getBlue() + (255 - color.getBlue()) * factor))
                );
            }

            private List<Color> generateVariants(Color base) {
                return IntStream.range(0, 6)
                        .mapToObj(i -> {
                            float hue = (i * 0.1f) % 1.0f;
                            float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
                            return Color.getHSBColor((hsb[0] + hue) % 1.0f, hsb[1], hsb[2]);
                        })
                        .toList();
            }
        }

    }

}
