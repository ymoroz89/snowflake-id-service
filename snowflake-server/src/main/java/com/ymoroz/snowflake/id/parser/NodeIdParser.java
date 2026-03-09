package com.ymoroz.snowflake.id.parser;

@FunctionalInterface
public interface NodeIdParser {

    long parse(String hostName);
}
