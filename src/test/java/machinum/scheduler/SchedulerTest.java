package machinum.scheduler;

import machinum.release.Release;
import machinum.release.ReleaseRepository;
import machinum.scheduler.ActionHandler.ActionsHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class SchedulerTest {

    @Mock
    ReleaseRepository repository;
    @Mock
    ActionsHandler actionHandler;

    Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(Executors.newScheduledThreadPool(1), repository, actionHandler);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    void testMain() {
        var flag = new AtomicBoolean();
        doAnswer(invocationOnMock -> {
            flag.set(Boolean.TRUE);

            return null;
        }).when(actionHandler)
                .handle(any());

        var release = Release.builder()
                .executed(false)
                .date(LocalDate.now())
                .build();

        scheduler.execute(release);

        assertThat(flag)
                .isTrue();
    }

}
