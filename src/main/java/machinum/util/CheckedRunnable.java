package machinum.util;

import org.apache.commons.lang3.exception.ExceptionUtils;

@FunctionalInterface
public interface CheckedRunnable {

    /**
     * Runs this operation.
     */
    void run() throws Exception;

    static Runnable checked(CheckedRunnable checkedRunnable) {
        return () -> {
            try {
                checkedRunnable.run();
            } catch (Exception e) {
                ExceptionUtils.rethrow(e);
            }
        };
    }

}
