package machinum.scheduler;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import machinum.book.Book;
import machinum.book.BookRepository;
import machinum.release.Release;
import machinum.release.Release.ReleaseTarget;
import machinum.release.ReleaseRepository;
import machinum.release.ReleaseRepository.ReleaseTargetRepository;
import machinum.telegram.TelegramHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface ActionHandler {

    void handle(ActionContext context);

    @Slf4j
    @RequiredArgsConstructor
    class ActionsHandler {

        private final TelegramHandler handler;
        private final ReleaseRepository repository;
        private final ReleaseTargetRepository targetRepository;
        private final BookRepository bookRepository;

        public void handle(Release release) {
            log.debug("Got request to execute action for release: {}", release);
            var targetName = release.getReleaseTargetName();
            var result = repository.findReleasePosition(release.getId());
            var releaseTarget = targetRepository.getById(release.getReleaseTargetId());
            var book = bookRepository.getById(releaseTarget.getBookId());

            var context = ActionContext.builder()
                    .release(release)
                    .releaseTarget(releaseTarget)
                    .book(book)
                    .isFirstRelease(result == 0)
                    .isLastRelease(result == 2)
                    .releasePosition(result)
                    .build();

            switch (targetName) {
                case "Telegram" -> {
                    handler.handle(context);
                }
                default -> throw new IllegalArgumentException("Unknown type: " + targetName);
            }

            repository.update(release);
        }

    }

    @Value
    @Builder
    class ActionContext {

        @Builder.Default
        Map<String, Object> data = new HashMap<>();
        Release release;
        ReleaseTarget releaseTarget;
        Book book;
        boolean isFirstRelease;
        boolean isLastRelease;
        int releasePosition;

        public <T> T get(String key) {
            return (T) data.get(key);
        }

        public <T> T getOr(String key, Supplier<T> defaultSupplier) {
            if (data.containsKey(key)) {
                return get(key);
            } else {
                T result = defaultSupplier.get();
                set(key, result);

                return result;
            }
        }

        public ActionContext set(String key, Object value) {
            data.put(key, value);

            return this;
        }

        public boolean isUsualRelease() {
            return !isFirstRelease && !isLastRelease;
        }

    }

}


