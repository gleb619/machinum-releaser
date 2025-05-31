package machinum.image.cover;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.util.function.Function;

@Data
@Builder(toBuilder = true)
@Slf4j
public class BookCover {

    @Builder.Default
    private int width = 1200;
    @Builder.Default
    private int height = 1800;
    @Builder.Default
    private Background background = Background.colorWash(Color.BLACK, 0.6f);
    @Builder.Default
    private Spine spine = Spine.left(Function.identity()).build();
    @Builder.Default
    private BrandMark brandMark = BrandMark.builder().build();


    public void renderToFile(String name, ImageFormat imageFormat) {
        new CoverRenderer(this).renderToFile(name, imageFormat);
    }

    public byte[] renderToBytes() {
        return new CoverRenderer(this).renderToBytes();
    }

    public enum ImageFormat {
        PNG, JPEG
    }

}
