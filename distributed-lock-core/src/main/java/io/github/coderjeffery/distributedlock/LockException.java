package io.github.coderjeffery.distributedlock;

/**
 * Base unchecked exception for lock operations.
 */
public class LockException extends RuntimeException {

    /**
     * @param message detail message
     */
    public LockException(String message) {
        super(message);
    }

    /**
     * @param message detail message
     * @param cause   underlying cause
     */
    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}
