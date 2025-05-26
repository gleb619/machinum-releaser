package machinum.util;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Builder
@Accessors(fluent = true, chain = true)
public class Pair<T1, T2> {

    T1 first;
    T2 second;

    public static <K, V> Pair<K, V> of(K key, V value) {
        return Pair.<K, V>builder()
                .first(key)
                .second(value)
                .build();
    }

    public T1 getKey() {
        return first;
    }

    public T2 getValue() {
        return second;
    }

}
