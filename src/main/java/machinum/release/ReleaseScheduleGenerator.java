package machinum.release;

import static machinum.release.Release.ReleaseConstants.PAGES_PARAM;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import machinum.release.Release.ReleaseTarget;

@Slf4j
public class ReleaseScheduleGenerator {

    public ReleaseTarget createTarget(String bookId, ReleaseScheduleRequest settings) {
        return ReleaseTarget.builder()
                .createdAt(LocalDateTime.now())
                .bookId(bookId)
                .name(settings.getName())
                .actionType(settings.getActionType())
                .metadata(settings.getMetadata())
                .build();
    }

    public List<Release> generate(String targetId, ReleaseScheduleRequest settings) {
        log.info("Got request to create schedule: date={}, name={}, mode={}", 
                LocalDateTime.now(), settings.getName(), settings.getGenerationMode());
        
        List<Release> rawSchedule = switch (settings.getModeConfig()) {
            case AnnuityModeConfig annuity -> generateAnnuitySchedule(settings);
            case LegacyModeConfig legacy -> generateLegacySchedule(settings);
            default -> throw new IllegalArgumentException("Unknown type of config: " + settings.getModeConfig().getClass());
        };

        return rebalance(rawSchedule, settings.getModeConfig().getSmoothFactor()).stream()
                .peek(r -> r.setReleaseTargetId(targetId))
                .collect(Collectors.toList());
    }

    public List<Release> generateSchedule(ReleaseScheduleRequest settings) {
        return switch (settings.getModeConfig()) {
            case AnnuityModeConfig annuity -> generateAnnuitySchedule(settings);
            case LegacyModeConfig legacy -> generateLegacySchedule(settings);
            default -> throw new IllegalArgumentException("Unknown type of config: " + settings.getModeConfig().getClass());
        };
    }

    /**
     * Legacy implementation that generates schedules using wave/peak patterns
     * with configurable bulk factors, smooth factors, and randomization.
     */
    private List<Release> generateLegacySchedule(ReleaseScheduleRequest settings) {
        List<Release> schedule = new ArrayList<>();
        double amplitude = (settings.getMaximal() - settings.getMinimal()) / 2.0;
        double offset = (settings.getMaximal() + settings.getMinimal()) / 2.0;
        int amount = settings.getModeConfig().getAmountOfChapters() - settings.getStart() - settings.getEnd();
        var initialDate = settings.getStartDate();

        int startValue = settings.getStart();
        schedule.add(Release.builder()
                .date(initialDate)
                .chapters(startValue)
                .build()
                .addMetadata(PAGES_PARAM, "1-" + startValue)
        );

        for (int i = 0; i < amount; ) {
            double progress = (double) i / amount;
            double adjustedProgress = Math.pow(progress, 1 / settings.getModeConfig().getPeakWidth());
            int chapters = (int) (Math.round(amplitude * Math.cos(settings.getPeriods() * 2 * Math.PI * adjustedProgress) + offset)
                    + randomize(settings.getModeConfig().getRandomFactor()));
            chapters = Math.max(settings.getMinimal(), Math.min(chapters, Math.min(settings.getMaximal(), amount - i)));
            initialDate = initialDate.plusDays(settings.getDayThreshold());
            schedule.add(Release.builder()
                    .date(initialDate)
                    .chapters(chapters)
                    .build()
                    .addMetadata(PAGES_PARAM, (startValue + i) + "-" + (startValue + i + chapters))
            );
            i += chapters;
        }

        int totalChapters = schedule.stream()
                .mapToInt(Release::getChapters)
                .sum();

        int lastValue = settings.getModeConfig().getAmountOfChapters() - totalChapters;
        schedule.add(Release.builder()
                .date(initialDate.plusDays(settings.getDayThreshold()))
                .chapters(lastValue)
                .build()
                .addMetadata(PAGES_PARAM, totalChapters + "-" + (totalChapters + lastValue))
        );

        return schedule;
    }

    /**
     * Annuity-style generation that splits chapters into equal chunks.
     * Allows control over start/end percentages and minimum chapters per chunk.
     */
    private List<Release> generateAnnuitySchedule(ReleaseScheduleRequest settings) {
        List<Release> schedule = new ArrayList<>();
        int totalChapters = settings.getModeConfig().getAmountOfChapters();
        int startChapters = settings.getStart();
        int endChapters = settings.getEnd();
        int middleChapters = totalChapters - startChapters - endChapters;
        
        var initialDate = settings.getStartDate();

        // Add start chunk
        if (startChapters > 0) {
            schedule.add(Release.builder()
                    .date(initialDate)
                    .chapters(startChapters)
                    .build()
                    .addMetadata(PAGES_PARAM, "1-" + startChapters)
            );
        }

        // Calculate equal chunks for the middle portion
        if (middleChapters > 0) {
            // Determine number of chunks based on min chapters constraint
            int maxChunks = middleChapters / settings.getMinChapters();
            int chunks = Math.max(1, maxChunks);
            
            int chunkSize = middleChapters / chunks;
            int remainder = middleChapters % chunks;

            int currentChapter = startChapters + 1;
            
            for (int i = 0; i < chunks; i++) {
                int chunkChapters = chunkSize + (i < remainder ? 1 : 0);
                initialDate = initialDate.plusDays(settings.getDayThreshold());
                
                schedule.add(Release.builder()
                        .date(initialDate)
                        .chapters(chunkChapters)
                        .build()
                        .addMetadata(PAGES_PARAM, currentChapter + "-" + (currentChapter + chunkChapters - 1))
                );
                
                currentChapter += chunkChapters;
            }
        }

        // Add end chunk
        if (endChapters > 0) {
            initialDate = initialDate.plusDays(settings.getDayThreshold());
            schedule.add(Release.builder()
                    .date(initialDate)
                    .chapters(endChapters)
                    .build()
                    .addMetadata(PAGES_PARAM, (totalChapters - endChapters + 1) + "-" + totalChapters)
            );
        }

        return schedule;
    }

    /* ============= */

    /**
     * Generates a random double value between 0 and the specified maximum.
     * The generated value can be either positive or negative with equal probability.
     *
     * @param max The maximum absolute value of the random number
     * @return A random double between -max and +max
     */
    private double randomize(double max) {
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
    private List<Release> rebalance(List<Release> releases, double smoothFactor) {
        if (releases == null || releases.isEmpty() || smoothFactor <= 0 || smoothFactor >= 1) {
            return releases;
        }

        List<Release> result = new ArrayList<>(releases.size());

        // Create copies of all objects to avoid modifying the original list
        for (Release release : releases) {
            result.add(release.copy(Function.identity()));
        }

        // Process each adjacent pair once
        for (int i = 0; i < result.size() - 1; i++) {
//            Release previous = i > 0 ? result.get(i - 1) : Release.builder()
//                    .chapters(1)
//                    .build();
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

        int chapters = 1;
        for (Release release : result) {
            release.addMetadata(PAGES_PARAM, chapters + "-" + (chapters + release.getChapters() - 1));
            chapters += release.getChapters();
        }

        return result;
    }

}
