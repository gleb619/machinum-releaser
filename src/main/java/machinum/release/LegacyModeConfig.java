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
 * Configuration for Legacy generation mode.
 * Maintains backward compatibility with existing wave/peak pattern generation.
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LegacyModeConfig implements GenerationModeConfig {

    @Min(1)
    @NotNull
    @NotEmpty
    private int amountOfChapters;

    @NotNull
    @Builder.Default
    private double startBulk = 0.1;

    @NotNull
    @Builder.Default
    private double endBulk = 0.1;

    @Min(1)
    @Builder.Default
    private int minChapters = 10;

    @Min(1)
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

    @Override
    public GenerationMode getGenerationMode() {
        return GenerationMode.LEGACY;
    }

    @Override
    public int getStart() {
        return (int) (getAmountOfChapters() * startBulk);
    }

    @Override
    public int getEnd() {
        return (int) (getAmountOfChapters() * endBulk);
    }

    @Override
    public int getMinimal() {
        return Math.min(getStart(), getMinChapters());
    }

    @Override
    public int getMaximal() {
        return Math.max(getEnd(), getMaxChapters());
    }

    @Override
    public int getPeriods() {
        return (int) Math.max(1, getAmountOfChapters() / (getAmountOfChapters() / getPeriodCount()));
    }

    @Override
    public double getPeakWidth() {
        return peakWidth;
    }

    @Override
    public double getRandomFactor() {
        return randomFactor;
    }

    @Override
    public double getSmoothFactor() {
        return smoothFactor;
    }

}