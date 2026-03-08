package machinum.release;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Interface for generation mode configurations.
 * Provides a unified API for accessing generation parameters
 * regardless of the specific mode implementation.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "generationMode"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = LegacyModeConfig.class, name = "LEGACY"),
    @JsonSubTypes.Type(value = AnnuityModeConfig.class, name = "ANNUITY")
})
public interface GenerationModeConfig {

    GenerationMode getGenerationMode();

    // Common calculation methods
    int getStart();
    int getEnd();
    int getMinimal();
    int getMaximal();
    int getPeriods();
    int getAmountOfChapters();

    // Legacy mode specific methods
    @Deprecated
    default double getPeakWidth() { return 0.4; }
    @Deprecated
    default double getRandomFactor() { return 0.3; }
    @Deprecated
    default double getSmoothFactor() { return 0.2; }

}