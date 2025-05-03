package machinum.release;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import lombok.*;
import machinum.Util.Pair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static machinum.Util.toPair;
import static machinum.release.Release.ReleaseConstants.PAGES_PARAM;

@Valid
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Release {

    private String id;
    private String releaseTargetName;
    private String releaseTargetId;
    private LocalDate date;
    private int chapters;
    private boolean executed;
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public <T> T metadata(String key) {
        return (T) metadata.get(key);
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    public boolean hasMetadata(String key, String value) {
        return hasMetadata(key) && Objects.equals(metadata(key), value);
    }

    public Release addMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    public Pair<Integer, Integer> toPageRequest() {
        String pages = metadata(PAGES_PARAM);

        return Arrays.stream(pages.split("-"))
                .map(Integer::parseInt)
                .collect(toPair());
    }

    public Release copy(Function<ReleaseBuilder, ReleaseBuilder> action) {
        return action.apply(toBuilder()).build();
    }


    @Valid
    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ReleaseTarget {

        private String id;
        private String bookId;
        private String name;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Map<String, Object> metadata = new HashMap<>();
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ReleaseView {

        @JsonUnwrapped
        @Builder.Default
        private ReleaseTarget releaseTarget = ReleaseTarget.builder().build();
        private int chaptersCount;
        private int releasesCount;
        private int releasesDays;
        @Builder.Default
        private LocalDate nextRelease = LocalDate.now();

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReleaseConstants {

        public static final String PAGES_PARAM = "pages";

    }

}
