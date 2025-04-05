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
    private String ruName;
    private String enName;
    private String originName;
    private String link;
    private String linkText;
    private String type;
    @Builder.Default
    private List<String> genre = new ArrayList<>();
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private Integer year;
    private Integer chapters;
    private String author;
    private String description;
    private String imageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
