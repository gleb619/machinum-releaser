package machinum.image.qr;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import machinum.image.qr.QRImage.GradientColorProvider;
import machinum.image.qr.QRImage.Logo;
import machinum.image.qr.QRImage.SolidColorProvider;
import machinum.image.qr.QRImage.TextInfo;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

class QRImageTest {

    @Test
    /**
     * Example main method demonstrating usage.
     * Requires a 'logo.png' file or change the logo part.
     */
    void testGenerate() {
        try {
            // Create a dummy logo image if 'logo.png' doesn't exist
            File logoFile = new File("logo.png");
            if (!logoFile.exists()) {
                BufferedImage dummyLogo = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = dummyLogo.createGraphics();
                g.setColor(Color.BLUE);
                g.fillOval(0, 0, 64, 64);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 40));
                g.drawString("G", 18, 45);
                g.dispose();
                ImageIO.write(dummyLogo, "png", logoFile);
                System.out.println("Created a dummy logo.png");
            }

            // --- Example 1: Basic ---
            QRImage.builder()
                    .data("https://www.example.com")
                    .build()
                    .saveToFile(Path.of("/tmp/qr_basic.png"));

            // --- Example 2: Gradient + Logo + Text ---
            QRImage.builder()
                    .data("https://github.com/google/zxing")
                    .width(500)
                    .height(500)
                    .foreground(GradientColorProvider.linear(Color.BLUE, Color.MAGENTA, 45.0))
//                    .background(SolidColorProvider.transparent())
                    .background(new SolidColorProvider(Color.WHITE))
                    .logo(Logo.fromFile("logo/Logo.png", 8, 0.20)) // 8px padding, 20% size
//                    .logo(Logo.fromFile("logo.png", 8, 0.20)) // 8px padding, 20% size
                    .textTop(TextInfo.of("ZXing Project", new Font(Font.SERIF, Font.BOLD, 28), Color.DARK_GRAY))
                    .textBottom(TextInfo.of("GitHub Link", new Font(Font.SANS_SERIF, Font.ITALIC, 20), Color.GRAY))
                    .build()
                    .saveToFile(Path.of("/tmp/qr_custom.png"));

            // --- Example 3: Transparent BG + Solid Color ---
            QRImage.builder()
                    .data("Java 21 + Lombok + ZXing")
                    .width(350)
                    .height(350)
                    .foreground(new SolidColorProvider(Color.decode("#007396"))) // Oracle Java Blue
                    .background(SolidColorProvider.transparent()) // Transparent
                    .margin(2)
                    .errorCorrection(ErrorCorrectionLevel.Q)
                    .build()
                    .saveToFile(Path.of("/tmp/qr_transparent.png"));

            System.out.println("QR Codes generated successfully!");

        } catch (Exception e) {
            System.err.println("Error generating QR code: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
