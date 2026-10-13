package io.github.coderjeffery.distributedlock;

/**
 * Thrown when unlocking a lock that is not held by the current thread.
 */
public class UnlockException extends LockException {

    /**
     * @param message detail message
     */
    public UnlockException(String message) {
        super(message);
    }
}
