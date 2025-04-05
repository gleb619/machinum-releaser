package machinum.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Valid
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Image {

    private String id;
    private String name;
    private String contentType;
    @NotNull
    @NotEmpty
    private byte[] data;
    private LocalDateTime createdAt;

}
