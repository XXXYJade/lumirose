package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.tool.args.EmptyParameters;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeToolTest {

    @Test
    void tc_n01_returnsIsoOffsetTimeToSecond() throws Exception {
        var tool = new TimeTool();

        String result = tool.execute(new EmptyParameters());

        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})"),
                "unexpected time format: " + result);
        assertEquals(0, ZonedDateTime.parse(result).getNano());
        assertEquals("get_current_time", tool.name());
        assertEquals(EmptyParameters.class, tool.parametersClass());
    }
}
