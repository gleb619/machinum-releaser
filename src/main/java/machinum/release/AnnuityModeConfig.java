package machinum.release;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for Annuity generation mode.
 * Implements equal chunk distribution with configurable start/end percentages.
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AnnuityModeConfig implements GenerationModeConfig {

    @Min(1)
    @NotNull
    @NotEmpty
    private int amountOfChapters;

    @Min(0)
    @Builder.Default
    private int startPercent = 10;

    @Min(0)
    @Builder.Default
    private int endPercent = 10;

    @Min(1)
    @Builder.Default
    private int minChapters = 10;

    @Override
    public GenerationMode getGenerationMode() {
        return GenerationMode.ANNUITY;
    }

    @Override
    public int getStart() {
        return (int) (getAmountOfChapters() * (startPercent / 100.0));
    }

    @Override
    public int getEnd() {
        return (int) (getAmountOfChapters() * (endPercent / 100.0));
    }

    @Override
    public int getMinimal() {
        return Math.min(getStart(), getMinChapters());
    }

    @Override
    public int getMaximal() {
        // Annuity mode doesn't have a max chapters constraint
        return getEnd();
    }

    @Override
    public int getPeriods() {
        // For annuity mode, periods calculation is not applicable in the same way
        // We'll return a default value or calculate based on available chapters
        int totalChapters = getAmountOfChapters();
        int startChapters = getStart();
        int endChapters = getEnd();
        int middleChapters = totalChapters - startChapters - endChapters;
        
        if (middleChapters <= 0) {
            return 1; // Only start and/or end chunks
        }
        
        // Calculate based on minimum chapters constraint
        return Math.max(1, middleChapters / getMinChapters());
    }

}