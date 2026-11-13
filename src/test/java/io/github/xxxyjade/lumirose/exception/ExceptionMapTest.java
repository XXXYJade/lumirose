package io.github.xxxyjade.lumirose.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionMapTest {

    @Test
    void tc_e01_everyCodeMapsToItsExceptionClass() {
        assertMapping(LumiRoseErrorCode.CONFIG_INVALID, new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID));
        assertMapping(LumiRoseErrorCode.PARAM_INVALID, new AgentException(LumiRoseErrorCode.PARAM_INVALID));

        assertMapping(LumiRoseErrorCode.CLIENT_HTTP_ERROR,
                new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, LumiRoseErrorCode.CLIENT_HTTP_ERROR.message()));
        assertMapping(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID,
                new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, LumiRoseErrorCode.CLIENT_RESPONSE_INVALID.message()));
        assertMapping(LumiRoseErrorCode.CLIENT_IO_ERROR,
                new ClientException(LumiRoseErrorCode.CLIENT_IO_ERROR, LumiRoseErrorCode.CLIENT_IO_ERROR.message()));

        assertMapping(LumiRoseErrorCode.AGENT_TURN_LIMIT, new AgentException(LumiRoseErrorCode.AGENT_TURN_LIMIT));
        assertMapping(LumiRoseErrorCode.AGENT_OUTPUT_TRUNCATED, new AgentException(LumiRoseErrorCode.AGENT_OUTPUT_TRUNCATED));
        assertMapping(LumiRoseErrorCode.AGENT_RESPONSE_INVALID, new AgentException(LumiRoseErrorCode.AGENT_RESPONSE_INVALID));

        assertMapping(LumiRoseErrorCode.TOOL_NOT_FOUND,
                new ToolException(LumiRoseErrorCode.TOOL_NOT_FOUND, LumiRoseErrorCode.TOOL_NOT_FOUND.message()));
        assertMapping(LumiRoseErrorCode.TOOL_ARGS_INVALID,
                new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, LumiRoseErrorCode.TOOL_ARGS_INVALID.message()));
        assertMapping(LumiRoseErrorCode.TOOL_EXECUTION_FAILED,
                new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, LumiRoseErrorCode.TOOL_EXECUTION_FAILED.message()));
        assertMapping(LumiRoseErrorCode.TOOL_PATH_INVALID,
                new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, LumiRoseErrorCode.TOOL_PATH_INVALID.message()));
        assertMapping(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND,
                new ToolException(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND, LumiRoseErrorCode.TOOL_FILE_NOT_FOUND.message()));
        assertMapping(LumiRoseErrorCode.TOOL_FILE_EXISTS,
                new ToolException(LumiRoseErrorCode.TOOL_FILE_EXISTS, LumiRoseErrorCode.TOOL_FILE_EXISTS.message()));
        assertMapping(LumiRoseErrorCode.TOOL_EDIT_NO_MATCH,
                new ToolException(LumiRoseErrorCode.TOOL_EDIT_NO_MATCH, LumiRoseErrorCode.TOOL_EDIT_NO_MATCH.message()));
        assertMapping(LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE,
                new ToolException(LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE, LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE.message()));

        assertMapping(LumiRoseErrorCode.STORAGE_FAILED, new StorageException(LumiRoseErrorCode.STORAGE_FAILED));
        assertMapping(LumiRoseErrorCode.INTERNAL_ERROR, new LumiRoseException(LumiRoseErrorCode.INTERNAL_ERROR));
    }

    @Test
    void tc_e02_clientExceptionCarriesStatusAndRetriable() {
        var throttled = new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, 429, "模型请求失败");
        assertEquals(429, throttled.httpStatus());
        assertTrue(throttled.retriable());

        var invalid = new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, 401, "模型请求失败");
        assertEquals(401, invalid.httpStatus());
        assertFalse(invalid.retriable());

        var serverError = new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, 503, "模型请求失败");
        assertTrue(serverError.retriable());

        var io = new ClientException(LumiRoseErrorCode.CLIENT_IO_ERROR, "网络异常", new IOException("refused"));
        assertTrue(io.retriable());
        assertTrue(io.getCause() instanceof IOException);
    }

    @Test
    void tc_e03_toolExceptionCarriesToolName() {
        var ex = new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, "read_file", "boom");
        assertEquals("read_file", ex.toolName());
        assertEquals(4003, ex.getErrorCode().code());
    }

    private void assertMapping(ErrorCode code, LumiRoseException ex) {
        assertEquals(code, ex.getErrorCode());
        assertEquals(code.code(), ex.getErrorCode().code());
        assertEquals(code.message(), ex.getMessage());
    }
}
