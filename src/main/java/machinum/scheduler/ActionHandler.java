package machinum.scheduler;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import machinum.release.Release;
import machinum.release.ReleaseRepository;
import machinum.telegram.TelegramHandler;

public interface ActionHandler {

    void handle(ActionContext context);

    @Slf4j
    @RequiredArgsConstructor
    class ActionsHandler {

        private final TelegramHandler handler;
        private final ReleaseRepository repository;

        public void handle(Release release) {
            log.debug("Got request to execute action for release: {}", release);
            var targetName = release.getReleaseTargetName();
            var result = repository.findReleasePosition(release.getId());
            var context = ActionContext.builder()
                    .release(release)
                    .isFirstRelease(result == 0)
                    .isLastRelease(result == 2)
                    .build();

            switch (targetName) {
                case "Telegram" -> {
                    handler.handle(context);
                }
                default -> throw new IllegalArgumentException("Unknown type: " + targetName);
            }
        }

    }

    @Value
    @Builder
    class ActionContext {

        Release release;
        boolean isFirstRelease;
        boolean isLastRelease;

        public boolean isUsualRelease() {
            return !isFirstRelease && !isLastRelease;
        }

    }

}


