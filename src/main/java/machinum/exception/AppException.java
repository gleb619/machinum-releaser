package machinum.exception;

public class AppException extends RuntimeException {

    public AppException() {
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
