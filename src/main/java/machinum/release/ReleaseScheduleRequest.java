package machinum.release;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import machinum.scheduler.ActionHandler.ActionType;

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

    private GenerationModeConfig modeConfig;

    private Map<String, Object> modeConfigMap = new LinkedHashMap<>();

    @NotNull
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public int getStart() {
        return modeConfig.getStart();
    }

    public int getEnd() {
        return modeConfig.getEnd();
    }

    public int getMinimal() {
        return modeConfig.getMinimal();
    }

    public int getMaximal() {
        return modeConfig.getMaximal();
    }

    public int getPeriods() {
        return modeConfig.getPeriods();
    }

    public int getMinChapters() {
        return modeConfig.getMinimal();
    }

    public GenerationMode getGenerationMode() {
        return modeConfig.getGenerationMode();
    }

    @JsonAnySetter
    public void setDetail(String key, Object value) {
        this.modeConfigMap.put(key, value);
    }

    public ReleaseScheduleRequest migrate(ObjectMapper objectMapper) {
        return toBuilder()
            .modeConfig(objectMapper.convertValue(modeConfigMap, GenerationModeConfig.class))
            .modeConfigMap(null)
            .build();
    }

}
