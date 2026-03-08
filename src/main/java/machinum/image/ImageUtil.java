package machinum.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageUtil {

  @SneakyThrows
  public static byte[] compressImage(byte[] imageBytes, int maxSize) {
    if (imageBytes.length <= maxSize) {
      return imageBytes;
    }

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
    ImageWriter writer = writers.next();
    ImageWriteParam param = writer.getDefaultWriteParam();

    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(0.9f);

    writer.setOutput(new MemoryCacheImageOutputStream(baos));
    writer.write(null, new IIOImage(image, null, null), param);
    writer.dispose();

    byte[] result = baos.toByteArray();
    if (result.length > maxSize) {
      return compressImage(result, maxSize);
    } else {
      return result;
    }
  }

}
