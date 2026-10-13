package io.github.coderjeffery.distributedlock;

/**
 * Thrown when a lock cannot be acquired within the requested wait time.
 */
public class LockTimeoutException extends LockException {

    /**
     * @param message detail message
     */
    public LockTimeoutException(String message) {
        super(message);
    }
}
