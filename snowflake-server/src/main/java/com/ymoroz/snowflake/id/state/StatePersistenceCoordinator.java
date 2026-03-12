package com.ymoroz.snowflake.id.state;

public interface StatePersistenceCoordinator {

    long initialize(long currentTimestamp);

    /**
     * Updates the pending reservation timestamp if the new candidate is larger.
     *
     * @param targetTimestamp timestamp to persist (e.g. current + buffer)
     */
    void persist(long targetTimestamp);

    void flushPending();

    void shutdown();
}
