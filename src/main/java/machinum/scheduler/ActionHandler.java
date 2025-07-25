package machinum.scheduler;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import machinum.book.Book;
import machinum.book.BookRepository;
import machinum.book.BookRestClient;
import machinum.exception.AppException;
import machinum.release.Release;
import machinum.release.Release.ReleaseStatus;
import machinum.release.Release.ReleaseTarget;
import machinum.release.ReleaseRepository;
import machinum.release.ReleaseRepository.ReleaseTargetRepository;
import machinum.telegram.TelegramHandler;
import machinum.website.WebsiteHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public interface ActionHandler {

    HandlerResult handle(ActionContext context);

    @Slf4j
    @RequiredArgsConstructor
    class ActionsHandler {

        private final WebsiteHandler websiteHandler;
        private final TelegramHandler tgHandler;
        private final ReleaseRepository repository;
        private final ReleaseTargetRepository targetRepository;
        private final BookRepository bookRepository;
        private final BookRestClient bookRestClient;

        public HandlerResult handle(Release release) {
            log.debug("Got request to execute action for release: {}", release);
            var actionType = ActionType.of(release.getReleaseActionType());
            var result = repository.findReleasePosition(release.getId());
            var releaseTarget = targetRepository.getById(release.getReleaseTargetId());
            var book = bookRepository.getById(releaseTarget.getBookId());
            var remoteBookId = getRemoteBookId(book.getUniqueId());

            var context = ActionContext.builder()
                    .release(release)
                    .releaseTarget(releaseTarget)
                    .actionType(actionType)
                    .book(book)
                    .isFirstRelease(result.getItemPosition() == 0)
                    .isLastRelease(result.getItemPosition() == 2)
                    .releasePosition(result.getIndex())
                    .remoteBookId(remoteBookId)
                    .build();

            //TODO return new copy of release in HandlerResult
            var output = switch (actionType) {
                case TELEGRAM, TELEGRAM_AUDIO -> tgHandler.handle(context);
                case WEBSITE -> websiteHandler.handle(context);
                default -> throw new IllegalArgumentException("Unknown type: " + actionType);
            };

            //TODO should we remove it, due Scheduler already have update call?
            repository.update(release);

            return output;
        }

        private String getRemoteBookId(String uniqueId) {
            var list = bookRestClient.getAllBookTitlesCached();
            return list.stream()
                    .filter(dto -> Objects.equals(dto.title(), uniqueId))
                    .findFirst()
                    .orElseThrow(() -> new AppException("Can't find remote book for given title: %s", uniqueId))
                    .id();
        }

    }

    @Value
    @Builder
    class HandlerResult {

        public ReleaseStatus status;

        @Singular("metadata")
        public Map<String, Object> metadata;

        public boolean isExecuted() {
            return Objects.equals(getStatus(), ReleaseStatus.EXECUTED);
        }

        public boolean hasNoChanges() {
            return Objects.equals(getStatus(), ReleaseStatus.NO_CHANGES);
        }

        public static HandlerResult executed() {
            return new HandlerResult(ReleaseStatus.EXECUTED, Map.of());
        }

        public static HandlerResult noChanges() {
            return new HandlerResult(ReleaseStatus.NO_CHANGES, Map.of());
        }

        public String statusString() {
            return status.name();
        }

    }

    @Value
    @Builder
    class ActionContext {

        @Builder.Default
        Map<String, Object> data = new HashMap<>();
        Release release;
        ReleaseTarget releaseTarget;
        ActionType actionType;
        Book book;
        boolean isFirstRelease;
        boolean isLastRelease;
        int releasePosition;
        String remoteBookId;

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

    enum ActionType {

        TELEGRAM,

        TELEGRAM_AUDIO,

        WEBSITE,

        OTHER
        ;

        public static ActionType of(String name) {
            var localName = name.toUpperCase();
            for (var actionType : values()) {
                if(localName.equals(actionType.name())) {
                    return actionType;
                }
            }

            return OTHER;
        }

    }


}


