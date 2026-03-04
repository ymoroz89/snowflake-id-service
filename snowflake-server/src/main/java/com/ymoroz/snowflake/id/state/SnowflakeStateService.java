package com.ymoroz.snowflake.id.state;

import com.ymoroz.snowflake.id.service.SnowflakeServiceImpl;

import java.nio.file.Path;

public interface SnowflakeStateService {
    long loadState(long currentTime);

    void saveState(long saveTime);
}
