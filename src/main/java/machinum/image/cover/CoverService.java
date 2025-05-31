package machinum.image.cover;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.image.Image;
import machinum.image.TriangleWrapper;
import machinum.image.cover.ColorSampler.ColorPalette;
import machinum.image.cover.Spine.SlotTextAlign;
import machinum.image.qr.QRImage;
import machinum.image.qr.QRImage.SolidColorProvider;
import machinum.image.qr.QRImage.TextInfo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;

import static machinum.image.cover.ColorSampler.calculateTextColor;
import static machinum.image.cover.ColorSampler.newAlpha;
import static machinum.image.cover.CoverRenderer.toBytes;
import static machinum.image.cover.FontSizeCalculator.calculateFont;

@Slf4j
public class CoverService {

    @SneakyThrows
    public Image generate(Image originImage) {
        Image resizedImage = ImageLoader.upscale(originImage);
        File originWithTriangles = TriangleWrapper.addTriangleEffect(resizedImage);
        BufferedImage inspiration = ImageLoader.mirror(originWithTriangles);

        return Image.builder()
                .name(originImage.getName() + "-cover")
                .contentType(originImage.getContentType())
                .data(toBytes(inspiration))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @SneakyThrows
    public Image generateBookCover(Image originImage, CoverInfo coverInfo) {
        double targetRatio = 5.0 / 7.0;
        Image resizedImage = ImageLoader.optionalResize(originImage,
                ResizeStrategy.replicate(15), targetRatio);

        BufferedImage inspiration = ImageLoader.load(resizedImage);

        BufferedImage origin = ImageLoader.load(resizedImage);
        ColorPalette palette = ColorSampler.extract(inspiration);
        ColorPalette altPalette = palette.invert();

        int spineWidth = Math.max(40, Math.round((float) origin.getWidth() / 6));
        Color brandColor = newAlpha(palette.getTopRightColor(), 25);
        Color brandBorderColor = newAlpha(calculateTextColor(palette.getTopRightColor(), false), 35);
        int sizeRatio = (int) (spineWidth * 0.4);
        int qrHeight = origin.getHeight() / 2;
        int qrWidth = qrHeight + (int) (qrHeight * 0.2);
        Font qrFont = calculateFont(coverInfo.qrTopText(), spineWidth + sizeRatio, spineWidth + sizeRatio,
                FontSizeCalculator.getBaseFont(), SlotTextAlign.HORIZONTALLY);
        Font qrBottomFont = calculateFont(coverInfo.qrBottomText(), spineWidth + sizeRatio, spineWidth + sizeRatio,
                FontSizeCalculator.getBaseFont(), SlotTextAlign.HORIZONTALLY);

        log.debug("""
                Generating cover:
                    canvas HxW = {}x{}
                    spine = {}
                    qr HxW = {}x{}
                    ratio = {}
                    fontSize = {}""", origin.getHeight(), origin.getWidth(), spineWidth, qrHeight, qrWidth, sizeRatio, qrFont.getSize());

        var cover = BookCover.builder()
                .width(origin.getWidth())
                .height(origin.getHeight())
                .background(Background.gradientFade(inspiration, Background.GradientDirection.RIGHT_TO_LEFT))
                .spine(Spine.left(s -> s
                                        .addTextSlot(b -> b
                                                .text(coverInfo.number())
                                                .slotGravity(Spine.SlotGravity.TOP))
                                        .addTextSlot(b -> b
                                                .text(coverInfo.text())
                                                .textAlign(SlotTextAlign.VERTICALLY))
                                        .addQRSlot(QRImage.builder()
                                                .data(coverInfo.qrContent())
                                                .width(qrWidth)
                                                .height(qrHeight)
                                                .foreground(SolidColorProvider.of(altPalette.getDominant())) // Oracle Java Blue
                                                .background(SolidColorProvider.transparent()) // Transparent
                                                .textTop(TextInfo.of(coverInfo.qrTopText(), qrFont, palette.getLightVariant()))
                                                .textBottom(TextInfo.of(coverInfo.qrBottomText(), qrBottomFont, palette.getLightVariant()))
                                                .margin(2)
                                                .errorCorrection(ErrorCorrectionLevel.Q)
                                                .build()
                                                .result())
//                                .addQRSlot(ImageLoader.load("~/Downloads/qr.png"))
                        )
                        .width(spineWidth)
                        .color(newAlpha(palette.getDarkVariant(), 200))
                        .withShadow(true)
                        .withHighlight(true)
                        .build())
                .brandMark(BrandMark.builder()
                        .text(coverInfo.brandText())
                        .color(brandColor)
                        .borderColor(brandBorderColor)
                        .build())
                .build();

        return Image.builder()
                .name(originImage.getName() + "-cover")
                .contentType(originImage.getContentType())
                .data(cover.renderToBytes())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public record CoverInfo(String number, String text, String brandText, String qrContent, String qrTopText,
                            String qrBottomText) {
    }

}
