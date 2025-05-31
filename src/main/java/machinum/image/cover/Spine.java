package machinum.image.cover;

import lombok.Builder;
import lombok.Data;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Data
@Builder(toBuilder = true)
public class Spine {

    @Builder.Default
    private SpinePosition position = SpinePosition.LEFT;
    @Builder.Default
    private int width = 40;
    @Builder.Default
    private Color color = Color.WHITE;
    @Builder.Default
    private boolean withShadow = false;
    @Builder.Default
    private boolean withHighlight = false;
    @Builder.Default
    private List<SpineSlot> slots = new ArrayList<>();

    public static SpineBuilder left(Function<Spine, Spine> customizer) {
        return customizer.apply(Spine.builder().build())
                .toBuilder();
    }

    public static SpineBuilder right(Function<Spine, Spine> customizer) {
        return customizer.apply(Spine.builder().position(SpinePosition.RIGHT).build())
                .toBuilder();
    }

    public Spine addTextSlot(Function<SpineSlot.SpineSlotBuilder, SpineSlot.SpineSlotBuilder> creator) {
        SpineSlot slot = creator.apply(SpineSlot.builder()).build();
        if (slot.getPosition() == -1) {
            slot.setPosition(this.slots.size());
        }

        if (slot.getText().length() > 50) {
            slot.setText(slot.getText().substring(0, 50));
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

    public enum SlotType {
        TEXT, IMAGE, QR_CODE
    }

    public enum SlotTextAlign {
        HORIZONTALLY, VERTICALLY
    }

    public enum SlotGravity {
        NONE, TOP, BOTTOM
    }

    @Data
    @Builder
    public static class SpineSlot {

        private BufferedImage image;
        private Font font;
        private Color textColor;
        @Builder.Default
        private SlotType type = SlotType.TEXT;
        @Builder.Default
        private String text = "";
        @Builder.Default
        private SlotTextAlign textAlign = SlotTextAlign.HORIZONTALLY;
        @Builder.Default
        private SlotGravity slotGravity = SlotGravity.NONE;
        @Builder.Default
        private int position = -1; // 0=top, 1=middle, 2=bottom

        public static SpineSlot text(String text, int position) {
            return builder()
                    .type(SlotType.TEXT)
                    .text(text)
                    .position(position)
                    .textAlign(SlotTextAlign.VERTICALLY)
                    .build();
        }

        public static SpineSlot image(BufferedImage image, int position) {
            return builder()
                    .type(SlotType.IMAGE)
                    .image(image)
                    .position(position)
                    .build();
        }

        public static SpineSlot qrCode(BufferedImage qrImage, int position) {
            return builder()
                    .type(SlotType.QR_CODE)
                    .image(qrImage)
                    .position(position)
                    .slotGravity(position > 1 ? SlotGravity.BOTTOM : SlotGravity.NONE)
                    .build();
        }

    }

}
