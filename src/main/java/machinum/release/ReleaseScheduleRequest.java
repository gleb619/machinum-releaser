package machinum.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import machinum.scheduler.ActionHandler;
import machinum.scheduler.ActionHandler.ActionType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Valid
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReleaseScheduleRequest {

    @NotNull
    @NotEmpty
    private ActionType actionType;

    @NotNull
    @NotEmpty
    private String name;

    @Builder.Default
    private LocalDate startDate = LocalDate.now();

    @Builder.Default
    private int dayThreshold = 4;

    @Min(1)
    @NotNull
    @NotEmpty
    private int amountOfChapters;

    @Builder.Default
    private double startBulk = 0.1;

    @Builder.Default
    private double endBulk = 0.1;

    @Builder.Default
    private int minChapters = 10;

    @Builder.Default
    private int maxChapters = 50;

    @Builder.Default
    private double peakWidth = 0.4;

    @Builder.Default
    private double smoothFactor = 0.2;

    @Builder.Default
    private double randomFactor = 0.3;

    @Builder.Default
    private double periodCount = 12.0;

    @Getter(lazy = true)
    private final int periods = (int) Math.max(1, getAmountOfChapters() / (getAmountOfChapters() / getPeriodCount()));

    @NotNull
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public int getStart() {
        return (int) (amountOfChapters * startBulk);
    }

    public int getEnd() {
        return (int) (amountOfChapters * endBulk);
    }

    public int getMinimal() {
        return Math.min(getStart(), getMinChapters());
    }

    public int getMaximal() {
        return Math.max(getEnd(), getMaxChapters());
    }

}
