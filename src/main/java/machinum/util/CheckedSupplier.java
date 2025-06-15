package machinum.util;

import lombok.SneakyThrows;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.function.Supplier;

@FunctionalInterface
public interface CheckedSupplier<T> {

    static <U> Supplier<U> checked(CheckedSupplier<U> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                return ExceptionUtils.rethrow(e);
            }
        };
    }

    T get() throws Exception;

    @SneakyThrows
    default T resolve() {
        return get();
    }

}
