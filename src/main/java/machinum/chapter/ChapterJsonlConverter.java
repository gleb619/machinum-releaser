package machinum.chapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ChapterJsonlConverter {

    @Getter
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public List<Chapter> fromString(String source) {
        var jsonObjects = new ArrayList<Chapter>();
        var lines = source.split("\\r?\\n");

        for (var line : lines) {
            var object = objectMapper.readValue(line, Chapter.class);
            jsonObjects.add(object);
        }

        return jsonObjects;
    }

    public <U> String toString(List<U> list) {
        return list.stream()
                .map(o -> {
                    try {
                        return objectMapper.writeValueAsString(o);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining("\n"));
    }

}
