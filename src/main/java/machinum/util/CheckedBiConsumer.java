package machinum.util;

import lombok.SneakyThrows;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

@FunctionalInterface
public interface CheckedBiConsumer<T, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     */
    void accept(T t, U u) throws Exception;

    static <F, S> BiConsumer<F, S> checked(CheckedBiConsumer<F, S> consumer) {
        return (f, s) -> {
            try {
                consumer.accept(f, s);
            } catch (Exception e) {
                ExceptionUtils.rethrow(e);
            }
        };
    }

}
