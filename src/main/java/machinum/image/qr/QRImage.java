package machinum.image.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Main class for generating customizable QR images using a fluent builder.
 */
@Getter
@Builder(toBuilder = true)
public class QRImage {

    @NonNull
    private String data;
    @Builder.Default
    private int width = 300;
    @Builder.Default
    private int height = 300;
    @Builder.Default
    private ColorProvider background = new SolidColorProvider(Color.WHITE);
    @Builder.Default
    private ColorProvider foreground = new SolidColorProvider(Color.BLACK);
    @Builder.Default
    private ErrorCorrectionLevel errorCorrection = ErrorCorrectionLevel.H;
    @Builder.Default
    private int margin = 1;

    private Logo logo;
    private TextInfo textTop;
    private TextInfo textBottom;

    @SneakyThrows
    public BufferedImage result() {
        return new QrRenderer(this).generate();
    }

    @SneakyThrows
    public void saveToFile(Path path) {
        new QrRenderer(this).saveToFile(path);
    }

    /**
     * Interface for providing color/paint to the QR code.
     */
    public interface ColorProvider {

        Paint getPaint(int width, int height);

    }

    /* ============= */

    @RequiredArgsConstructor
    public static class QrRenderer {

        private final QRImage qrImage;

        /**
         * Generates the QR code image based on the builder configuration.
         *
         * @return A BufferedImage containing the customized QR code.
         * @throws WriterException If there's an error during QR code matrix generation.
         * @throws IOException     If there's an error loading the logo image.
         */
        public BufferedImage generate() throws WriterException, IOException {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, qrImage.errorCorrection);
            hints.put(EncodeHintType.MARGIN, qrImage.margin);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrImage.data, BarcodeFormat.QR_CODE, qrImage.width, qrImage.height, hints);

            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();

            // --- Calculate Text Heights ---
            int textTopHeight = 0;
            int textBottomHeight = 0;
            BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gTemp = tempImage.createGraphics();
            FontMetrics fm;

            if (qrImage.textTop != null) {
                gTemp.setFont(qrImage.textTop.getFont());
                textTopHeight = gTemp.getFontMetrics().getHeight() + 10; // + padding
            }
            if (qrImage.textBottom != null) {
                gTemp.setFont(qrImage.textBottom.getFont());
                textBottomHeight = gTemp.getFontMetrics().getHeight() + 10; // + padding
            }
            gTemp.dispose();
            int totalHeight = qrImage.height + textTopHeight + textBottomHeight;
            int qrYOffset = textTopHeight;

            // --- Create Image and Graphics ---
            BufferedImage image = new BufferedImage(qrImage.width, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();

            // --- Set Rendering Hints ---
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // --- Draw Background ---
            graphics.setPaint(qrImage.background.getPaint(qrImage.width, totalHeight));
            graphics.fillRect(0, 0, qrImage.width, totalHeight);

            // --- Draw QR Modules ---
            graphics.setPaint(qrImage.foreground.getPaint(qrImage.width, qrImage.height));
            double moduleWidth = (double) qrImage.width / matrixWidth;
            double moduleHeight = (double) qrImage.height / matrixHeight;

            for (int i = 0; i < matrixWidth; i++) {
                for (int j = 0; j < matrixHeight; j++) {
                    if (bitMatrix.get(i, j)) {
                        int x = (int) Math.round(i * moduleWidth);
                        int y = (int) Math.round(j * moduleHeight) + qrYOffset;
                        int w = (int) Math.round((i + 1) * moduleWidth) - x;
                        int h = (int) Math.round((j + 1) * moduleHeight) - y;
                        graphics.fillRect(x, y, Math.max(1, w), Math.max(1, h));
                    }
                }
            }

            // --- Draw Logo ---
            renderLogo(qrYOffset, graphics);

            // --- Draw Text ---
            renderText(graphics, textTopHeight);

            graphics.dispose();
            return image;
        }

        private void renderLogo(int qrYOffset, Graphics2D graphics) {
            if (qrImage.logo != null && qrImage.logo.getImage() != null) {
                BufferedImage logoImage = qrImage.logo.getImage();
                int logoWidth = (int) (qrImage.width * qrImage.logo.getScale());
                int logoHeight = (int) (qrImage.height * qrImage.logo.getScale());
                int logoX = (qrImage.width - logoWidth) / 2;
                int logoY = ((qrImage.height - logoHeight) / 2) + qrYOffset;
                int padding = qrImage.logo.getPadding();
                float arc = 20f; // Roundness factor

                // Draw a background/border for the logo
                graphics.setColor(qrImage.background instanceof SolidColorProvider ? ((SolidColorProvider) qrImage.background).getColor() : Color.WHITE);
                graphics.fill(new RoundRectangle2D.Float(
                        logoX - padding, logoY - padding,
                        logoWidth + 2 * padding, logoHeight + 2 * padding,
                        arc + padding, arc + padding));

                // Clip to draw the rounded logo
                RoundRectangle2D clipShape = new RoundRectangle2D.Float(logoX, logoY, logoWidth, logoHeight, arc, arc);
                graphics.setClip(clipShape);
                graphics.drawImage(logoImage, logoX, logoY, logoWidth, logoHeight, null);
                graphics.setClip(null); // Reset clip
            }
        }

        private void renderText(Graphics2D graphics, int textTopHeight) {
            FontMetrics fm;
            Rectangle2D textBounds;
            if (qrImage.textTop != null) {
                graphics.setFont(qrImage.textTop.getFont());
                graphics.setColor(qrImage.textTop.getColor());
                fm = graphics.getFontMetrics();
                textBounds = fm.getStringBounds(qrImage.textTop.getContent(), graphics);
                int textX = (qrImage.width - (int) textBounds.getWidth()) / 2;
                int textY = fm.getAscent() + 5;
                graphics.drawString(qrImage.textTop.getContent(), textX, textY);
            }

            if (qrImage.textBottom != null) {
                graphics.setFont(qrImage.textBottom.getFont());
                graphics.setColor(qrImage.textBottom.getColor());
                fm = graphics.getFontMetrics();
                textBounds = fm.getStringBounds(qrImage.textBottom.getContent(), graphics);
                int textX = (qrImage.width - (int) textBounds.getWidth()) / 2;
                int textY = qrImage.height + textTopHeight + fm.getAscent() + 5;
                graphics.drawString(qrImage.textBottom.getContent(), textX, textY);
            }
        }

        /**
         * Saves the generated QR code image to a file.
         *
         * @param path The path where the image should be saved.
         * @throws IOException     If an error occurs during file writing.
         * @throws WriterException If an error occurs during QR code generation.
         */
        public void saveToFile(Path path) throws IOException, WriterException {
            BufferedImage image = generate();
            String fileName = path.getFileName().toString();
            String format = "png"; // Default
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                format = fileName.substring(dotIndex + 1);
            }
            ImageIO.write(image, format, path.toFile());
        }

    }

    /**
     * Provides a solid color.
     */
    @Value
    public static class SolidColorProvider implements ColorProvider {

        Color color;

        public static SolidColorProvider of(Color color) {
            return new SolidColorProvider(color);
        }

        public static SolidColorProvider transparent() {
            return of(new Color(255, 255, 255, 0));
        }

        @Override
        public Paint getPaint(int width, int height) {
            return color;
        }

    }

    /**
     * Provides a gradient color.
     */
    @Value
    public static class GradientColorProvider implements ColorProvider {

        Color startColor;
        Color endColor;
        float startX, startY, endX, endY;

        public static GradientColorProvider linear(Color start, Color end, double angle) {
            // Simple angle to points conversion (can be improved)
            double rad = Math.toRadians(angle);
            return new GradientColorProvider(start, end, 0, 0, (float) Math.cos(rad), (float) Math.sin(rad));
        }

        @Override
        public Paint getPaint(int width, int height) {
            // Scale gradient points to image size
            return new GradientPaint(
                    startX * width, startY * height, startColor,
                    endX * width, endY * height, endColor
            );
        }

    }

    /**
     * Represents an embedded logo.
     */
    @Value
    public static class Logo {

        BufferedImage image;
        int padding;
        double scale; // e.g., 0.20 for 20%

        public static Logo fromFile(String path, int padding, double scale) throws IOException {
            return new Logo(ImageIO.read(new File(path)), padding, scale);
        }

        public static Logo fromImage(BufferedImage image, int padding, double scale) {
            return new Logo(image, padding, scale);
        }

    }

    /**
     * Represents text annotation.
     */
    @Value
    public static class TextInfo {

        String content;
        Font font;
        Color color;

        public static TextInfo of(String content, Font font, Color color) {
            return new TextInfo(content, font, color);
        }

        public static TextInfo of(String content) {
            return new TextInfo(content, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Color.BLACK);
        }

    }

}
