package machinum.scheduler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import machinum.release.Release;
import machinum.release.ReleaseRepository;
import machinum.scheduler.ActionHandler.ActionsHandler;

import java.time.LocalDate;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static machinum.Util.firstNonNull;
import static machinum.Util.runAsync;

@Slf4j
@RequiredArgsConstructor
public class Scheduler implements AutoCloseable {

    private final ScheduledExecutorService executor;
    private final ReleaseRepository repository;
    private final ActionsHandler actionHandler;

    public void init() {
        runAsync(() -> {
            var list = repository.findAllToExecute();
            if (!list.isEmpty()) {
                log.info("Prepare to schedule execution for: {} items", list.size());
                list.forEach(this::execute);
            }
        });
    }

    public void executeAsync(Release release) {
        runAsync(() -> execute(release));
    }

    public void execute(@NonNull Release release) {
        LocalDate targetDate = release.getDate();
        if (release.isExecuted()) {
            log.info("Task already executed, skipping: id={}", release.getId());
            return;
        }

        log.info("Scheduling execution of task: id={}, target={}", release.getId(), firstNonNull(release.getReleaseTargetName(), release.getReleaseTargetId()));
        String releaseId = release.getId();

        if (targetDate.isEqual(LocalDate.now()) || targetDate.isBefore(LocalDate.now())) {
            log.info("Task meets day condition, runs immediately: id={}", releaseId);
            runTask(releaseId);
        } else {
            long delay = calculateDelay(targetDate);
            log.info("Scheduling execution: taskId={}, days to wait={}", releaseId, delay);
            executor.schedule(() -> runTask(releaseId), delay, TimeUnit.DAYS);
        }
    }

    private long calculateDelay(LocalDate targetDate) {
        return targetDate.toEpochDay() - LocalDate.now().toEpochDay();
    }

    @Synchronized
    private void runTask(String releaseId) {
        repository.findById(releaseId).ifPresentOrElse(release -> {
            if (release.isExecuted()) {
                log.info("Task already executed during check, skipping: id={}", releaseId);
                return;
            }

            try {
                log.info("Execution task: id={}", releaseId);
                actionHandler.handle(release);
                release.setExecuted(true);
                repository.markAsExecuted(releaseId);
                log.info("Executed task: id={}", releaseId);
            } catch (Exception e) {
                log.error("ERROR: ", e);
            }
        }, () -> log.warn("Release with id={} not found", releaseId));
    }

    @Override
    public void close() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }

}
