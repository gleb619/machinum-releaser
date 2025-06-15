package machinum.chapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ChapterJsonlConverter {

    @Getter
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public List<Chapter> fromString(@NonNull String source) {
        if (source.isEmpty()) {
            throw new AppException("Chapters content is missing");
        }
        var jsonObjects = new ArrayList<Chapter>();
        var lines = source.split("\\r?\\n");

        for (var line : lines) {
            try {
                var object = objectMapper.readValue(line, Chapter.class);
                jsonObjects.add(object);
            } catch (Exception e) {
                log.error("Can't read object: {}", line);
            }
        }

        return jsonObjects;
    }

    public <U> String toString(List<U> list) {
        return list.stream()
                .map(o -> {
                    try {
                        return objectMapper.writeValueAsString(o);
                    } catch (JsonProcessingException e) {
                        return ExceptionUtils.rethrow(e);
                    }
                })
                .collect(Collectors.joining("\n"));
    }

}
