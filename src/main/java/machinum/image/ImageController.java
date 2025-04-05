package machinum.image;

import io.jooby.Context;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

import java.util.Objects;

@Path("/api")
public class ImageController {

    public static final String PLACEHOLDER = "00000000-0000-0000-0000-000000000000";

    @SneakyThrows
    @GET("/images/{id}")
    public void image(@PathParam("id") String id,
                      Context ctx) {
        if (PLACEHOLDER.equals(id)) {
            @Cleanup
            var resourceAsStream = ImageController.class.getResourceAsStream("/web/image/cover.jpg");
            if (Objects.nonNull(resourceAsStream)) {
                ctx.setResponseHeader("Content-Type", "image/jpg");
                ctx.send(IOUtils.toByteArray(resourceAsStream));

                return;
            }
        }

        var repository = ctx.require(ImageRepository.class);

        var image = repository.findById(id);
        if (image.isPresent()) {
            Image img = image.get();
            ctx.setResponseHeader("Content-Type", img.getContentType());
            ctx.send(img.getData());
        } else {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
        }
    }

    @POST("/images")
    @Consumes("multipart/form-data")
    public Image createImage(Context ctx) {
        var repository = ctx.require(ImageRepository.class);

        var file = ctx.file("image");
        var result = repository.create(Image.builder()
                .name(file.getFileName())
                .contentType(file.getContentType())
                .data(file.bytes())
                .build());

        ctx.setResponseCode(StatusCode.CREATED);
        return Image.builder()
                .id(result)
                .build();
    }

}
