package machinum.release;

import static machinum.scheduler.ActionHandler.ActionType.TELEGRAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.SneakyThrows;
import machinum.scheduler.ActionHandler.ActionType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ReleaseScheduleGeneratorTest {

    ReleaseScheduleGenerator releaseScheduleGenerator = new ReleaseScheduleGenerator();
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    public void testCreateTarget() {
        String bookId = "12345";
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Release")
                .actionType(TELEGRAM)
                .build();

        Release.ReleaseTarget target = releaseScheduleGenerator.createTarget(bookId, settings);

        assertNotNull(target);
        assertEquals(LocalDateTime.now().getYear(), target.getCreatedAt().getYear());
        assertEquals(bookId, target.getBookId());
        assertEquals(settings.getName(), target.getName());
        assertEquals(ActionType.TELEGRAM, target.getActionType());
    }

    @Test
    public void testGenerateLegacy() {
        String targetId = "target123";
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Schedule")
                .actionType(TELEGRAM)
                .startDate(LocalDate.now())
                .modeConfig(LegacyModeConfig.builder()
                    .amountOfChapters(200)
                    .build())
                .build();

        List<Release> schedule = releaseScheduleGenerator.generate(targetId, settings);

        assertNotNull(schedule);
        assertFalse(schedule.isEmpty());
        assertEquals(targetId, schedule.get(0).getReleaseTargetId());
        assertEquals(GenerationMode.LEGACY, settings.getGenerationMode());
    }

    @Test
    public void testGenerateAnnuity() {
        String targetId = "target123";
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Schedule")
                .actionType(TELEGRAM)
                .startDate(LocalDate.now())
                .modeConfig(AnnuityModeConfig.builder()
                    .amountOfChapters(100)
                    .build())
                .build();

        // Test that the modeConfig is properly initialized
        assertNotNull(settings.getModeConfig());
        assertEquals(GenerationMode.ANNUITY, settings.getModeConfig().getGenerationMode());
        
        // Just test that it doesn't throw an exception
        List<Release> schedule = releaseScheduleGenerator.generate(targetId, settings);

        assertNotNull(schedule);
        assertFalse(schedule.isEmpty());
        assertEquals(targetId, schedule.get(0).getReleaseTargetId());
        assertEquals(GenerationMode.ANNUITY, settings.getGenerationMode());
    }

    @Test
    public void testGenerateScheduleLegacy() {
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Schedule")
                .actionType(TELEGRAM)
                .startDate(LocalDate.now())
                .modeConfig(LegacyModeConfig.builder()
                    .amountOfChapters(10)
                    .build())
                .build();

        List<Release> schedule = releaseScheduleGenerator.generateSchedule(settings);

        assertNotNull(schedule);
        assertFalse(schedule.isEmpty());
        assertEquals(GenerationMode.LEGACY, settings.getGenerationMode());
    }

    @Test
    public void testGenerateScheduleAnnuity() {
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Schedule")
                .actionType(TELEGRAM)
                .startDate(LocalDate.now())
                .modeConfig(AnnuityModeConfig.builder()
                    .amountOfChapters(100)
                    .build())
                .build();

        List<Release> schedule = releaseScheduleGenerator.generateSchedule(settings);

        assertNotNull(schedule);
        assertFalse(schedule.isEmpty());
        assertEquals(GenerationMode.ANNUITY, settings.getGenerationMode());
    }

    @Test
    @SneakyThrows
    public void testJsonDeserialization() {
        String json = """
            {
                "name": "TEst",
                "actionType": "TELEGRAM",
                "startDate": "2026-02-14",
                "dayThreshold": 2,
                "amountOfChapters": 441,
                "generationMode": "ANNUITY",
                "startBulk": 0.1,
                "endBulk": 0.1,
                "minChapters": 20,
                "maxChapters": 50,
                "peakWidth": 0.4,
                "smoothFactor": 0.2,
                "randomFactor": 0.3,
                "periodCount": 12,
                "startPercent": 10,
                "endPercent": 20,
                "metadata": {
                    "chatType": "TEST"
                }
            }
        """;
        ReleaseScheduleRequest scheduleRequest = objectMapper.readValue(json, ReleaseScheduleRequest.class)
            .migrate(objectMapper);
        assertNotNull(scheduleRequest);

        Assertions.assertThat(scheduleRequest.getModeConfig())
            .isNotNull()
            .isInstanceOf(AnnuityModeConfig.class)
            .isEqualTo(AnnuityModeConfig.builder()
                .amountOfChapters(441)
                .startPercent(10)
                .endPercent(20)
                .minChapters(20)
                .build());
    }

}
