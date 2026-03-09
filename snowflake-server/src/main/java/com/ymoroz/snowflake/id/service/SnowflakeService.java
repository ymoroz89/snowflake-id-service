package com.ymoroz.snowflake.id.service;

@FunctionalInterface
public interface SnowflakeService {

    long nextId();
}
