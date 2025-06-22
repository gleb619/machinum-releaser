package machinum.util;

import machinum.release.Release;

import java.util.Map;
import java.util.Objects;

@FunctionalInterface
public interface MetadataSupport<U extends MetadataSupport<U>> {

    Map<String, Object> getMetadata();

    default <T> T metadata(String key) {
        return (T) getMetadata().get(key);
    }

    default <T> T metadata(String key, T defaultValue) {
        return (T) getMetadata().getOrDefault(key, defaultValue);
    }

    default boolean hasMetadata(String key) {
        return getMetadata().containsKey(key);
    }

    default boolean hasMetadata(String key, String value) {
        return hasMetadata(key) && Objects.equals(metadata(key), value);
    }

    default U addMetadata(String key, Object value) {
        getMetadata().put(key, value);
        return (U) this;
    }

}
