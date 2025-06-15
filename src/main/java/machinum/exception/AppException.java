package machinum.exception;

import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class AppException extends RuntimeException {

    private final Map<String, Object> metadata = new HashMap<>();

    public AppException() {
    }

    public AppException(String message, Map<String, Object> metadata) {
        super(message);
        this.metadata.putAll(metadata);
    }

    public AppException(String message, Object... args) {
        super(message.formatted(args));
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }

    public AppException(Throwable cause) {
        super(cause);
    }

}
