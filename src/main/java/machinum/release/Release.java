package machinum.release;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import lombok.*;
import machinum.util.MetadataSupport;
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
public class Release implements MetadataSupport<Release> {

    private String id;
    private String releaseTargetName;
    private String releaseTargetId;
    private LocalDate date;
    private int chapters;
    @Deprecated(forRemoval = true)
    private boolean executed;
    private String status;
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Pair<Integer, Integer> toPageRequest() {
        String pages = metadata(PAGES_PARAM);

        return Arrays.stream(pages.split("-"))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(toPair());
    }

    public ReleaseStatus status() {
        return ReleaseStatus.valueOf(getStatus());
    }

    public void status(ReleaseStatus status) {
        setStatus(status.name());
    }

    public Release copy(Function<ReleaseBuilder, ReleaseBuilder> action) {
        return action.apply(toBuilder()).build();
    }

    public enum ReleaseStatus {

        DRAFT,
        MANUAL_ACTION_REQUIRED,
        EXECUTED

    }

    @Valid
    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ReleaseTarget implements MetadataSupport<ReleaseTarget> {

        private String id;
        private String bookId;
        private String name;
        private boolean enabled;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Map<String, Object> metadata = new HashMap<>();
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();
        @Builder.Default
        private LocalDateTime updatedAt = LocalDateTime.now();

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
        public static final String SITE_URL_PARAM = "siteUrl";
        public static final String SITE_PORT_PARAM = "sitePort";

    }

}
