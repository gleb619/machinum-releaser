package machinum.release;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import lombok.*;
import machinum.util.Pair;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.zip.CRC32;

import static machinum.release.Release.ReleaseConstants.PAGES_PARAM;
import static machinum.util.Util.toPair;

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
        private Boolean enabled;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Map<String, Object> metadata = new HashMap<>();
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();

        public String getDiscriminatorId() {
            CRC32 fileCRC32 = new CRC32();
            String key = "%s-%s-%s".formatted(getBookId(), getName(), getMetadata());
            fileCRC32.update(key.getBytes(StandardCharsets.UTF_8));
            return String.format(Locale.US,"%08X", fileCRC32.getValue());
        }

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
        private boolean disabled;
        @Builder.Default
        private LocalDate nextRelease = LocalDate.now();

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReleaseConstants {

        public static final String PAGES_PARAM = "pages";

    }

}
