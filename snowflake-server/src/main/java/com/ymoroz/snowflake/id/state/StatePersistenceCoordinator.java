package com.ymoroz.snowflake.id.state;

public interface StatePersistenceCoordinator {

    long initialize(long currentTimestamp);

    void persist(long currentTimestamp);

    void shutdown();
}
