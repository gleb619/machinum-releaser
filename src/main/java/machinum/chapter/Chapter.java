package machinum.chapter;

import lombok.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Chapter {

    private Integer number;

    private String translatedTitle;

    @ToString.Exclude
    private String translatedText;

}
