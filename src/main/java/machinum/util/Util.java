package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;

import javax.xml.bind.DatatypeConverter;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

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

    // Helper method to check if a string is non-empty
    public static boolean isNonEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    public static void runAsync(Runnable runnable) {
        CompletableFuture.runAsync(runnable)
                .whenComplete((unused, e) -> {
                    if (Objects.nonNull(e)) {
                        log.error("ERROR: ", e);
                    }
                });
    }

    public static <T, K> List<T> saveSortOrder(List<K> order, List<T> list, Function<T, K> extractor) {
        List<T> output = new ArrayList<>();
        for (K k : order) {
            for (T t : list) {
                K key = extractor.apply(t);
                if (key.equals(k)) {
                    output.add(t);
                }
            }
        }

        return output;
    }

    public static <T> Collector<T, List<T>, Pair<T, T>> toPair() {
        return new Collector<>() {
            @Override
            public Supplier<List<T>> supplier() {
                return ArrayList::new;
            }

            @Override
            public BiConsumer<List<T>, T> accumulator() {
                return (list, item) -> {
                    if (list.size() <= 2) {
                        list.add(item);
                    }
                };
            }

            @Override
            public BinaryOperator<List<T>> combiner() {
                return (list1, list2) -> {
                    List<T> result = new ArrayList<>(list1);
                    list2.stream().limit(2 - list1.size())
                            .forEach(result::add);

                    return result;
                };
            }

            @Override
            public Function<List<T>, Pair<T, T>> finisher() {
                return list -> {
                    if (list.size() < 2) {
                        throw new AppException("Expected exactly 2 elements, but got " + list.size());
                    }
                    return new Pair<>(list.get(0), list.get(1));
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (var entry : map.entrySet()) {
            if (value == null ? entry.getValue() == null : value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Automatically detects and converts string values to their appropriate Java types
     * using regex patterns for preliminary checks before attempting parsing.
     * Supports Integer, Double, Boolean, and String types.
     *
     * @param value the string value to convert
     * @return the converted object with its appropriate type
     */
    public static Object typedParse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        // Regex patterns
        String booleanPattern = "^(true|false)$";
        String integerPattern = "^[-+]?\\d+$";
        String doublePattern = "^[-+]?\\d*\\.?\\d+([eE][-+]?\\d+)?$";

        // Check for boolean values with regex
        if (value.toLowerCase().matches(booleanPattern)) {
            return Boolean.parseBoolean(value);
        }

        // Check for integer with regex first
        if (value.matches(integerPattern)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Fallback to long if the number is too large for an int
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ex) {
                    // If still too large, it will be handled as a double or string
                }
            }
        }

        // Check for double with regex first
        if (value.matches(doublePattern)) {
            try {
                Double doubleValue = Double.parseDouble(value);

                // If the double is actually an integer value, return as an integer
                if (doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)) {
                    if (doubleValue >= Integer.MIN_VALUE && doubleValue <= Integer.MAX_VALUE) {
                        return doubleValue.intValue();
                    }
                }

                return doubleValue;
            } catch (NumberFormatException e) {
                // Not a valid double, continue
            }
        }

        // Return as string if no other type matches
        return value;
    }

    public static boolean hasCause(@NonNull Throwable rootCause, @NonNull Class<?> clazz) {
        var throwable = rootCause;
        int depth = 0;

        while (throwable != null && depth < 20) {
            if (clazz.isInstance(throwable)) {
                return true;
            }
            throwable = throwable.getCause();
            depth++;
        }

        return false;
    }

    @SneakyThrows
    public static String addQueryParam(String url, String paramName, String paramValue) {
        URI uri = new URI(url);
        String query = uri.getQuery();
        String newQuery;

        if (query == null || query.isEmpty()) {
            newQuery = paramName + "=" + paramValue;
        } else {
            newQuery = query + "&" + paramName + "=" + paramValue;
        }

        return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                uri.getPath(), newQuery, uri.getFragment()).toString();
    }

    @SneakyThrows
    public static String md5(String text) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(text.getBytes());
        byte[] digest = md.digest();
        return DatatypeConverter.printHexBinary(digest).toUpperCase();
    }

}
