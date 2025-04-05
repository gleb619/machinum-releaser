package machinum;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Util {

    public static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new NullPointerException("All values are null");
    }

    public static void runAsync(Runnable runnable) {
        CompletableFuture.runAsync(runnable)
                .whenComplete((unused, e) -> {
                    if (Objects.nonNull(e)) {
                        log.error("ERROR: ", e);
                    }
                });
    }

    /* ============= */

    @FunctionalInterface
    public interface Try<T, R> {

        static <I, U> Function<I, U> of(Try<I, U> aTry) {
            return i -> {
                try {
                    return aTry.apply(i);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        R apply(T t) throws Exception;

    }

}
