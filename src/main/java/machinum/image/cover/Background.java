package machinum.image.cover;

import lombok.Builder;
import lombok.Data;

import java.awt.*;
import java.awt.image.BufferedImage;

@Data
@Builder
public class Background {

    private BufferedImage sourceImage;
    @Builder.Default
    private BackgroundType type = BackgroundType.GRADIENT_FADE;
    @Builder.Default
    private Color color = Color.BLACK;
    @Builder.Default
    private int blurRadius = 25;
    @Builder.Default
    private float opacity = 0.8f;
    @Builder.Default
    private GradientDirection gradientDirection = GradientDirection.RIGHT_TO_LEFT;

    public static Background expandFrom(BufferedImage image) {
        return Background.builder()
                .type(BackgroundType.BLURRED_EXPANSION)
                .sourceImage(image)
                .blurRadius(15)
                .build();
    }

    public static Background colorWash(Color color, float opacity) {
        return Background.builder()
                .type(BackgroundType.COLOR_WASH)
                .color(color)
                .opacity(opacity)
                .build();
    }

    public static Background gradientFade(BufferedImage image, GradientDirection direction) {
        return Background.builder()
                .type(BackgroundType.GRADIENT_FADE)
                .sourceImage(image)
                .gradientDirection(direction)
                .build();
    }

    public enum BackgroundType {
        SOLID_COLOR, BLURRED_EXPANSION, COLOR_WASH, GRADIENT_FADE
    }

    public enum GradientDirection {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, RADIAL
    }

}
