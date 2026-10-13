package io.github.coderjeffery.distributedlock;

/**
 * Creates {@link DistributedLock} instances. Implementations should be thread-safe
 * and may be used as a Spring singleton.
 */
public interface DistributedLockClient extends AutoCloseable {

    /**
     * Returns a lock for the given logical name.
     *
     * @param name lock name, must not be blank
     * @return lock handle; cheap to call repeatedly for the same name
     */
    DistributedLock getLock(String name);

    @Override
    void close();
}
