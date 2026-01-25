package machinum.chapter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Chapter {

    public static final String TEXT = "text";
    public static final String VALUE = "value";

    private Integer number;

    private String translatedTitle;

    @ToString.Exclude
    private String translatedText;

    @Builder.Default
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnySetter
    public void addAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        return (T) additionalProperties.getOrDefault(key, defaultValue);
    }

}
