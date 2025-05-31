package machinum.image.cover;

import lombok.Builder;
import lombok.Data;

import java.awt.*;

@Data
@Builder
public class BrandMark {

    @Builder.Default
    private String text = "";
    @Builder.Default
    private Color color = Color.WHITE;
    @Builder.Default
    private Color borderColor = Color.BLACK;
    @Builder.Default
    private CornerPosition position = CornerPosition.TOP_RIGHT;


    public enum CornerPosition {
        TOP_LEFT, TOP_RIGHT
    }

}
