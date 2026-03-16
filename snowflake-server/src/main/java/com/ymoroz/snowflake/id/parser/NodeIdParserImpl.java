package com.ymoroz.snowflake.id.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class NodeIdParserImpl implements NodeIdParser {
    @Override
    public long parse(String hostName) {
        return Optional.ofNullable(hostName)
                .filter(h -> !h.isEmpty())
                .map(h -> h.split("-"))
                .filter(parts -> parts.length > 0)
                .map(parts -> parts[parts.length - 1])
                .map(Long::parseLong)
                .orElseThrow(() -> {
                    log.error("Invalid hostName: {}", hostName);
                    return new IllegalArgumentException("Invalid hostName: " + hostName);
                });
    }
}
