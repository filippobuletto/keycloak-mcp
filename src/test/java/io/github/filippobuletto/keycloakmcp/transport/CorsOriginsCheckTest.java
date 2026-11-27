package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorsOriginsCheckTest {

    final List<LogRecord> warnings = new CopyOnWriteArrayList<>();
    final Logger logger = Logger.getLogger(CorsOriginsCheck.class.getName());
    final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void capture() {
        logger.addHandler(capture);
    }

    @AfterEach
    void release() {
        logger.removeHandler(capture);
    }

    static String text(LogRecord record) {
        Object[] params = record.getParameters();
        return params == null ? record.getMessage() : String.format(record.getMessage(), params);
    }

    @Test
    void warnsOncePerRegexOrigin() {
        new CorsOriginsCheck(Optional.of(List.of("https://a.example", CorsOriginsTest.REGEX)));
        assertEquals(1, warnings.size(), warnings.toString());
        String message = text(warnings.get(0));
        assertTrue(message.startsWith("MCP_CORS_ORIGINS trusts every origin matching the regular expression "
                + CorsOriginsTest.REGEX), message);
    }

    @Test
    void exactOriginsStartWithoutWarnings() {
        new CorsOriginsCheck(Optional.of(List.of("https://a.example")));
        assertEquals(List.of(), warnings);
    }

    @Test
    void noOriginsStartWithoutWarnings() {
        new CorsOriginsCheck(Optional.empty());
        assertEquals(List.of(), warnings);
    }

    @Test
    void bareWildcardStopsTheStart() {
        assertThrows(IllegalStateException.class, () -> new CorsOriginsCheck(Optional.of(List.of("*"))));
    }
}
