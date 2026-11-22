package io.github.xxxyjade.lumirose.exception;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.ClientException;
import io.github.xxxyjade.lumirose.common.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.common.exception.StorageException;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import org.junit.jupiter.api.Test;

import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.AGENT_CANCELLED;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.AGENT_COMPRESSION_FAILED;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.AGENT_OUTPUT_TRUNCATED;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.AGENT_RESPONSE_INVALID;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.AGENT_TURN_LIMIT;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.CLIENT_HTTP_ERROR;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.CLIENT_IO_ERROR;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.CLIENT_RESPONSE_INVALID;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.CONFIG_INVALID;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.INTERNAL_ERROR;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.PARAM_INVALID;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.STORAGE_FAILED;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_ARGS_INVALID;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_EDIT_NO_MATCH;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_EXECUTION_FAILED;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_FILE_EXISTS;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_FILE_NOT_FOUND;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_NOT_FOUND;
import static io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode.TOOL_PATH_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTest {

    @Test
    void a18_codeMapping() {
        assertMapping(CONFIG_INVALID, new ConfigurationException(CONFIG_INVALID, "x"), ConfigurationException.class);
        assertMapping(PARAM_INVALID, new AgentException(PARAM_INVALID, "x"), AgentException.class);
        assertMapping(CLIENT_HTTP_ERROR, new ClientException(CLIENT_HTTP_ERROR, "x"), ClientException.class);
        assertMapping(CLIENT_RESPONSE_INVALID, new ClientException(CLIENT_RESPONSE_INVALID, "x"), ClientException.class);
        assertMapping(CLIENT_IO_ERROR, new ClientException(CLIENT_IO_ERROR, "x"), ClientException.class);
        assertMapping(AGENT_TURN_LIMIT, new AgentException(AGENT_TURN_LIMIT, "x"), AgentException.class);
        assertMapping(AGENT_OUTPUT_TRUNCATED, new AgentException(AGENT_OUTPUT_TRUNCATED, "x"), AgentException.class);
        assertMapping(AGENT_RESPONSE_INVALID, new AgentException(AGENT_RESPONSE_INVALID, "x"), AgentException.class);
        assertMapping(AGENT_CANCELLED, new AgentException(AGENT_CANCELLED, "x"), AgentException.class);
        assertMapping(AGENT_COMPRESSION_FAILED, new AgentException(AGENT_COMPRESSION_FAILED, "x"), AgentException.class);
        assertMapping(TOOL_NOT_FOUND, new ToolException(TOOL_NOT_FOUND, "tool", "x"), ToolException.class);
        assertMapping(TOOL_ARGS_INVALID, new ToolException(TOOL_ARGS_INVALID, "tool", "x"), ToolException.class);
        assertMapping(TOOL_EXECUTION_FAILED, new ToolException(TOOL_EXECUTION_FAILED, "tool", "x"), ToolException.class);
        assertMapping(TOOL_PATH_INVALID, new ToolException(TOOL_PATH_INVALID, "tool", "x"), ToolException.class);
        assertMapping(TOOL_FILE_NOT_FOUND, new ToolException(TOOL_FILE_NOT_FOUND, "tool", "x"), ToolException.class);
        assertMapping(TOOL_FILE_EXISTS, new ToolException(TOOL_FILE_EXISTS, "tool", "x"), ToolException.class);
        assertMapping(TOOL_EDIT_NO_MATCH, new ToolException(TOOL_EDIT_NO_MATCH, "tool", "x"), ToolException.class);
        assertMapping(TOOL_EDIT_NOT_UNIQUE, new ToolException(TOOL_EDIT_NOT_UNIQUE, "tool", "x"), ToolException.class);
        assertMapping(STORAGE_FAILED, new StorageException(STORAGE_FAILED, "x", new RuntimeException()), StorageException.class);
        assertMapping(INTERNAL_ERROR, new LumiRoseException(INTERNAL_ERROR, "x"), LumiRoseException.class);
    }

    @Test
    void a18_retriableClassification() {
        assertTrue(new ClientException(CLIENT_IO_ERROR, "x").retriable());
        assertTrue(new ClientException(CLIENT_HTTP_ERROR, 429, "x").retriable());
        assertTrue(new ClientException(CLIENT_HTTP_ERROR, 500, "x").retriable());
        assertTrue(new ClientException(CLIENT_HTTP_ERROR, 503, "x").retriable());

        assertFalse(new ClientException(CLIENT_HTTP_ERROR, "x").retriable());
        assertFalse(new ClientException(CLIENT_HTTP_ERROR, 400, "x").retriable());
        assertFalse(new ClientException(CLIENT_HTTP_ERROR, 401, "x").retriable());
        assertFalse(new ClientException(CLIENT_HTTP_ERROR, 404, "x").retriable());
    }

    @Test
    void a18_toolNamePreserved() {
        ToolException exception = new ToolException(TOOL_EXECUTION_FAILED, "read_file", "x");

        assertEquals("read_file", exception.toolName());
        assertSame(TOOL_EXECUTION_FAILED, exception.getErrorCode());
    }

    @Test
    void a18_messagesDoNotLeakApiKey() {
        String apiKey = "sk-test-secret";

        for (LumiRoseErrorCode code : LumiRoseErrorCode.values()) {
            assertFalse(code.defaultMessage().contains(apiKey));
        }

        LumiRoseException exception = new ClientException(CLIENT_HTTP_ERROR, 500, "模型请求失败");
        assertFalse(exception.getMessage().contains(apiKey));
    }

    private static void assertMapping(LumiRoseErrorCode code,
                                      LumiRoseException exception,
                                      Class<? extends LumiRoseException> expectedType) {
        assertInstanceOf(expectedType, exception);
        assertSame(code, exception.getErrorCode());
        assertEquals(code.code(), exception.getErrorCode().code());
    }
}
