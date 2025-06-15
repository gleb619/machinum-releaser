package machinum.util;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.function.Function;

@FunctionalInterface
public interface Try<T, R> {

    static <I, U> Function<I, U> of(Try<I, U> aTry) {
        return i -> {
            try {
                return aTry.apply(i);
            } catch (Exception e) {
                return ExceptionUtils.rethrow(e);
            }
        };
    }

    R apply(T t) throws Exception;

}
