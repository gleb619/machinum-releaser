package machinum.image.cover;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import machinum.exception.AppException;
import machinum.image.cover.BrandMark.CornerPosition;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class CoverRenderer {

    private final BookCover cover;

    @SneakyThrows
    public static byte[] toBytes(BufferedImage image) {
        var baos = new ByteArrayOutputStream(1024);
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public BufferedImage render() {
        BufferedImage coverImage = new BufferedImage(cover.getWidth(), cover.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = coverImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Render background
        renderBackground(g2d);

        // Render spine
        if (cover.getSpine() != null && cover.getSpine().getPosition() != Spine.SpinePosition.NONE) {
            renderSpine(g2d);
        }

        if (cover.getBrandMark() != null) {
            renderBrand(g2d);
        }

        g2d.dispose();
        return coverImage;
    }

    private void renderBackground(Graphics2D g2d) {
        if (cover.getBackground() == null) {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, cover.getWidth(), cover.getHeight());
            return;
        }

        switch (cover.getBackground().getType()) {
            case SOLID_COLOR -> {
                g2d.setColor(cover.getBackground().getColor());
                g2d.fillRect(0, 0, cover.getWidth(), cover.getHeight());
            }
            case BLURRED_EXPANSION -> renderBlurredBackground(g2d);
            case COLOR_WASH -> renderColorWash(g2d);
            case GRADIENT_FADE -> renderGradientFade(g2d);
        }
    }

    private void renderBlurredBackground(Graphics2D g2d) {
        if (cover.getBackground().getSourceImage() == null) return;

        BufferedImage scaled = scaleImage(cover.getBackground().getSourceImage(), cover.getWidth(), cover.getHeight());
        BufferedImage blurred = applyGaussianBlur(scaled, cover.getBackground().getBlurRadius());
        g2d.drawImage(blurred, 0, 0, null);
    }

    private void renderColorWash(Graphics2D g2d) {
        g2d.setColor(cover.getBackground().getColor());
        g2d.fillRect(0, 0, cover.getWidth(), cover.getHeight());

        if (cover.getBackground().getSourceImage() != null) {
            Composite original = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, cover.getBackground().getOpacity()));
            BufferedImage scaled = scaleImage(cover.getBackground().getSourceImage(), cover.getWidth(), cover.getHeight());
            g2d.drawImage(scaled, 0, 0, null);
            g2d.setComposite(original);
        }
    }

    private void renderGradientFade(Graphics2D g2d) {
        if (cover.getBackground().getSourceImage() == null || cover.getBackground().getGradientDirection() == null)
            return;

        BufferedImage scaled = scaleImage(cover.getBackground().getSourceImage(), cover.getWidth(), cover.getHeight());
        g2d.drawImage(scaled, 0, 0, null);

        GradientPaint gradient = switch (cover.getBackground().getGradientDirection()) {
            case LEFT_TO_RIGHT ->
                    new GradientPaint(0, 0, new Color(0, 0, 0, 0), cover.getWidth(), 0, new Color(0, 0, 0, 180));
            case RIGHT_TO_LEFT ->
                    new GradientPaint(cover.getWidth(), 0, new Color(0, 0, 0, 0), 0, 0, new Color(0, 0, 0, 180));
            case TOP_TO_BOTTOM ->
                    new GradientPaint(0, 0, new Color(0, 0, 0, 0), 0, cover.getHeight(), new Color(0, 0, 0, 180));
            case RADIAL ->
                    new GradientPaint(cover.getWidth() / 2f, cover.getHeight() / 2f, new Color(0, 0, 0, 0), cover.getWidth(), cover.getHeight(), new Color(0, 0, 0, 180));
        };

        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, cover.getWidth(), cover.getHeight());
    }

    private void renderSpine(Graphics2D g2d) {
        int spineX = cover.getSpine().getPosition() == Spine.SpinePosition.LEFT ? 0 : cover.getWidth() - cover.getSpine().getWidth();

        // Base spine color
        g2d.setColor(cover.getSpine().getColor() != null ? cover.getSpine().getColor() : Color.DARK_GRAY);
        g2d.fillRect(spineX, 0, cover.getSpine().getWidth(), cover.getHeight());

        // Shadow effect
        if (cover.getSpine().isWithShadow()) {
            LinearGradientPaint shadowGradient = new LinearGradientPaint(
                    spineX, 0, spineX + cover.getSpine().getWidth(), 0,
                    new float[]{0.0f, 1.0f},
                    new Color[]{new Color(0, 0, 0, 80), new Color(0, 0, 0, 0)}
            );
            g2d.setPaint(shadowGradient);
            g2d.fillRect(spineX, 0, cover.getSpine().getWidth(), cover.getHeight());
        }

        // Highlight effect
        if (cover.getSpine().isWithHighlight()) {
            g2d.setColor(new Color(255, 255, 255, 40));
            int highlightX = cover.getSpine().getPosition() == Spine.SpinePosition.LEFT ? spineX + cover.getSpine().getWidth() - 1 : spineX;
            g2d.fillRect(highlightX, 0, 1, cover.getHeight());
        }

        // Render spine slots
        renderSpineSlots(g2d, spineX);
    }

    private void renderSpineSlots(Graphics2D g2d, int spineX) {
        if (cover.getSpine().getSlots().isEmpty()) return;

        int slotHeight = cover.getHeight() / 3;

        for (Spine.SpineSlot slot : cover.getSpine().getSlots()) {
            if (slot.getPosition() > 2) {
                throw new AppException("Spine allows only 3 position, found: %s", slot.getPosition());
            }
            int slotY = slot.getPosition() * slotHeight;

            // Calculate dynamic font for spine slots if not set
            if (slot.getType() == Spine.SlotType.TEXT && slot.getFont() == null) {
                Font baseFont = FontSizeCalculator.getBaseFont();
                Font dynamicFont = FontSizeCalculator.calculateFont(
                        slot.getText(), cover.getSpine().getWidth(), slotHeight, baseFont, slot.getTextAlign());
                slot.setFont(dynamicFont);
            }

            switch (slot.getType()) {
                case TEXT -> renderSpineText(g2d, slot, spineX, slotY, slotHeight);
                case IMAGE, QR_CODE -> renderSpineImage(g2d, slot, spineX, slotY, slotHeight);
            }
        }
    }

    private void renderSpineText(Graphics2D g2d, Spine.SpineSlot slot, int spineX, int slotY, int slotHeight) {
        if (slot.getText() == null || slot.getText().isEmpty()) return;

        Font font;
        if (slot.getFont() != null) {
            font = slot.getFont();
        } else {
            font = FontSizeCalculator.getBaseFont();
        }
        g2d.setFont(font);

        // Calculate text color based on spine background
        Color textColor;
        if (Objects.isNull(slot.getTextColor())) {
            textColor = ColorSampler.calculateTextColor(cover.getSpine().getColor(), true);
        } else {
            textColor = slot.getTextColor();
        }
        g2d.setColor(textColor);

        FontMetrics fm = g2d.getFontMetrics();

        if (slot.getTextAlign() == Spine.SlotTextAlign.VERTICALLY) {
            renderVerticalText(g2d, slot.getText(), spineX + cover.getSpine().getWidth() / 2, slotY + slotHeight / 2, fm);
        } else {
            renderHorizontalSpineText(g2d, slot, spineX, slotY, slotHeight, fm);
        }
    }

    private void renderSpineImage(Graphics2D g2d, Spine.SpineSlot slot, int spineX, int slotY, int slotHeight) {
        if (slot.getImage() == null) return;

        int imageSize = Math.min(cover.getSpine().getWidth() - 4, slotHeight - 4);
        int imageX = spineX + (cover.getSpine().getWidth() - imageSize) / 2;
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

    private void renderHorizontalSpineText(Graphics2D g2d, Spine.SpineSlot slot, int spineX, int slotY, int slotHeight, FontMetrics fm) {
        int textWidth = fm.stringWidth(slot.getText());
        int textX = spineX + (cover.getSpine().getWidth() - textWidth) / 2;
        int textY = slotY + slotHeight / 2 + fm.getAscent() / 2;
        textY = switch (slot.getSlotGravity()) {
            case TOP -> textY - slotHeight / 4;
            case BOTTOM -> textY + slotHeight / 4;
            default -> textY;
        };

        g2d.drawString(slot.getText(), textX, textY);
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

    /* ============= */

    public void renderBrand(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        String text = cover.getBrandMark().getText();
        CornerPosition position = cover.getBrandMark().getPosition();
        List<String> lines = Arrays.asList(text.split("\n"));

        if (lines.isEmpty() || text.trim().isEmpty()) {
            return;
        }

        double angle = Math.toRadians(45); // Consider adjusting angle based on position if needed
        Point textAnchor = switch (position) {
            case TOP_LEFT -> new Point((int) (cover.getWidth() * 0.10), (int) (cover.getHeight() * 0.08));
            case TOP_RIGHT -> new Point((int) (cover.getWidth() * 0.90), (int) (cover.getHeight() * 0.08));
        };

        Font font = FontSizeCalculator.calculateOptimalFont(text, (int) (cover.getWidth() * 0.06),
                (int) (cover.getHeight() * 0.07), FontSizeCalculator.getBaseFont(), false);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();

        Color borderColor = cover.getBrandMark().getBorderColor(); // Should have transparency
        Color textColor = cover.getBrandMark().getColor();

        AffineTransform originalTransform = g2d.getTransform();
        g2d.translate(textAnchor.x, textAnchor.y);
        g2d.rotate(angle);

        FontRenderContext frc = g2d.getFontRenderContext();
        float borderThickness = Math.max(Math.min(5f, (float) cover.getWidth() / 100), 1);
        Stroke borderStroke = new BasicStroke(borderThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        int currentY = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                currentY += lineHeight;
                continue;
            }

            TextLayout textLayout = new TextLayout(line, font, frc);
            int lineWidth = fm.stringWidth(line);
            int x = -lineWidth / 2;

            // Get the outline shape at (0, 0)
            Shape textShape = textLayout.getOutline(null);

            // Create a transform to move the shape to its centered (x, currentY) position
            AffineTransform textTransform = AffineTransform.getTranslateInstance(x, currentY);
            Shape translatedShape = textTransform.createTransformedShape(textShape);

            // Store original stroke and composite
            Stroke originalStroke = g2d.getStroke();
            Composite originalComposite = g2d.getComposite();

            // Ensure transparency works correctly
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

            // Draw the border (outline)
            g2d.setColor(borderColor);
            g2d.setStroke(borderStroke);
            g2d.draw(translatedShape);

            // Fill the text
            g2d.setColor(textColor);
            g2d.fill(translatedShape);

            // Restore original stroke and composite
            g2d.setStroke(originalStroke);
            g2d.setComposite(originalComposite);

            currentY += lineHeight;
        }

        g2d.setTransform(originalTransform);
    }

    @SneakyThrows
    public void renderToFile(String filename, BookCover.ImageFormat format) {
        BufferedImage rendered = render();
        String formatName = format == BookCover.ImageFormat.PNG ? "PNG" : "JPEG";
        ImageIO.write(rendered, formatName, new File(filename));
    }

    public byte[] renderToBytes() {
        return toBytes(render());
    }

}
