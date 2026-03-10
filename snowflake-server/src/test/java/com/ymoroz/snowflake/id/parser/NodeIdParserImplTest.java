package com.ymoroz.snowflake.id.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeIdParserImplTest {

    private final NodeIdParserImpl parser = new NodeIdParserImpl();

    @Test
    void testParseValidHostname() {
        assertEquals(1, parser.parse("snowflake-1"));
        assertEquals(123, parser.parse("node-123"));
        assertEquals(5, parser.parse("snowflake-id-service-5"));
    }

    @Test
    void testParseNullHostname() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void testParseEmptyHostname() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }

    @Test
    void testParseNonNumericHostname() {
        assertThrows(NumberFormatException.class, () -> parser.parse("snowflake-node"));
    }

    @Test
    void testParseHostnameWithoutDash() {
        assertEquals(10, parser.parse("10"));
    }

    @Test
    void testParseHostnameTrailingDash() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("snowflake-"));
    }
}
