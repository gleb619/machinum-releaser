package machinum.release;

import lombok.extern.slf4j.Slf4j;
import machinum.release.Release.ReleaseTarget;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ReleaseScheduleGenerator {

    public static List<Release> generateSchedule(ReleaseScheduleRequest settings) {
        List<Release> schedule = new ArrayList<>();
        double amplitude = (settings.getMaxChapters() - settings.getMinChapters()) / 2.0;
        double offset = (settings.getMaxChapters() + settings.getMinChapters()) / 2.0;
        int amount = settings.getAmountOfChapters() - settings.getStart() - settings.getEnd();
        var initialDate = settings.getStartDate();

        schedule.add(Release.builder()
                .date(initialDate)
                .chapters(settings.getStart())
                .build());

        for (int i = 0; i < amount; ) {
            double progress = (double) i / amount;
            double adjustedProgress = Math.pow(progress, 1 / settings.getPeakWidth());
            int chapters = (int) (Math.round(amplitude * Math.cos(settings.getPeriods() * 2 * Math.PI * adjustedProgress) + offset)
                    + randomize(settings.getRandomFactor()));
            chapters = Math.max(settings.getMinChapters(), Math.min(chapters, Math.min(settings.getMaxChapters(), amount - i)));
            initialDate = initialDate.plusDays(settings.getDayThreshold());
            schedule.add(Release.builder()
                    .date(initialDate)
                    .chapters(chapters)
                    .build());
            i += chapters;
        }

        int totalChapters = schedule.stream()
                .mapToInt(Release::getChapters)
                .sum();

        schedule.add(Release.builder()
                .date(initialDate.plusDays(settings.getDayThreshold()))
                .chapters(settings.getAmountOfChapters() - totalChapters)
                .build());

        return schedule;
    }

    /**
     * Generates a random double value between 0 and the specified maximum.
     * The generated value can be either positive or negative with equal probability.
     *
     * @param max The maximum absolute value of the random number
     * @return A random double between -max and +max
     */
    public static double randomize(double max) {
        // Generate a random value between 0 and max
        double randomValue = Math.random() * max;

        // Randomly determine if the value should be negative or positive
        boolean isNegative = Math.random() > 0.5;

        // Return either positive or negative value
        return isNegative ? -randomValue : randomValue;
    }

    /**
     * Smooths the differences between adjacent chapter values in a list of Release objects.
     * The method preserves the total sum of chapters while reducing gaps between adjacent items
     * based on the provided smoothFactor.
     *
     * @param releases     List of Release objects to be smoothed
     * @param smoothFactor A factor between 0 and 1 that determines the smoothing intensity
     * @return A new list with smoothed chapter values
     */
    public static List<Release> rebalance(List<Release> releases, double smoothFactor) {
        if (releases == null || releases.isEmpty() || smoothFactor <= 0 || smoothFactor >= 1) {
            return releases;
        }

        List<Release> result = new ArrayList<>(releases.size());

        // Create copies of all objects to avoid modifying the original list
        for (Release release : releases) {
            result.add(release.copy(b -> b.chapters(release.getChapters())));
        }

        // Process each adjacent pair once
        for (int i = 0; i < result.size() - 1; i++) {
            Release current = result.get(i);
            Release next = result.get(i + 1);

            int currentChapters = current.getChapters();
            int nextChapters = next.getChapters();

            // Calculate difference and adjustment
            int diff = currentChapters - nextChapters;
            int adjustment = (int) Math.round(diff * smoothFactor);

            // Apply adjustment
            current.setChapters(currentChapters - adjustment);
            next.setChapters(nextChapters + adjustment);
        }

        return result;
    }

    public List<Release> generate(String targetId, ReleaseScheduleRequest settings) {
        log.info("Got request to create schedule: date={}, name={}", LocalDateTime.now(), settings.getName());
        var rawSchedule = generateSchedule(settings);

        return rebalance(rawSchedule, settings.getSmoothFactor()).stream()
                .peek(r -> r.setReleaseTargetId(targetId))
                .collect(Collectors.toList());
    }

    public ReleaseTarget createTarget(String bookId, ReleaseScheduleRequest settings) {
        return ReleaseTarget.builder()
                .createdAt(LocalDateTime.now())
                .bookId(bookId)
                .name(settings.getName())
                .build();
    }

}
