package machinum.util;

import lombok.SneakyThrows;

import java.util.function.Supplier;

@FunctionalInterface
public interface CheckedSupplier<T> {

    static <U> Supplier<U> checked(CheckedSupplier<U> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    T get() throws Exception;

    @SneakyThrows
    default T resolve() {
        return get();
    }

}
