package machinum.release;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseScheduleGeneratorTest {

    ReleaseScheduleGenerator releaseScheduleGenerator = new ReleaseScheduleGenerator();

    @Test
    public void testCreateTarget() {
        String bookId = "12345";
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Release")
                .build();

        Release.ReleaseTarget target = releaseScheduleGenerator.createTarget(bookId, settings);

        assertNotNull(target);
        assertEquals(LocalDateTime.now().getYear(), target.getCreatedAt().getYear());
        assertEquals(bookId, target.getBookId());
        assertEquals(settings.getName(), target.getName());
    }

    @Test
    public void testGenerate() {
        String targetId = "target123";
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Schedule")
                .startDate(LocalDate.now())
                .amountOfChapters(200)
                .build();

        List<Release> schedule = releaseScheduleGenerator.generate(targetId, settings);

        assertNotNull(schedule);
        assertFalse(schedule.isEmpty());
        assertEquals(targetId, schedule.get(0).getReleaseTargetId());
    }

    @Test
    public void testGenerateSchedule() {
        ReleaseScheduleRequest settings = ReleaseScheduleRequest.builder()
                .name("Test Schedule")
                .startDate(LocalDate.now())
                .amountOfChapters(10)
                .build();

        List<Release> schedule = releaseScheduleGenerator.generateSchedule(settings);

        assertNotNull(schedule);
        assertFalse(schedule.isEmpty());
    }

}
