package machinum.util;

import java.util.function.Function;

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
