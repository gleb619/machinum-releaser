package machinum.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Valid
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Book {

    private String id;
    @NotNull
    @NotEmpty
    private String uniqueId;
    @NotNull
    @NotEmpty
    private String ruName;
    @NotNull
    @NotEmpty
    private String enName;
    @NotNull
    @NotEmpty
    private String originName;
    @NotNull
    @NotEmpty
    private String link;
    @NotNull
    @NotEmpty
    private String linkText;
    @NotNull
    @NotEmpty
    private String type;
    @NotNull
    @NotEmpty
    @Builder.Default
    private List<String> genre = new ArrayList<>();
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    @NotNull
    @NotEmpty
    private Integer year;
    @NotNull
    @NotEmpty
    private Integer chapters;
    @NotNull
    @NotEmpty
    private String author;
    private String description;
    @NotNull
    @NotEmpty
    private String imageId;
    @NotNull
    @NotEmpty
    private String imageData;
    @NotNull
    @NotEmpty
    private String originImageId;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

}
