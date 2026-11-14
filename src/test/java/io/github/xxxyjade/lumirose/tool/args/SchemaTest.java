package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tc_s01_scalarsMapToJsonTypes() throws Exception {
        assertSchema(ScalarParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "string": {"type": "string"},
                    "count": {"type": "integer"},
                    "big": {"type": "integer"},
                    "ratio": {"type": "number"},
                    "flag": {"type": "boolean"}
                  }
                }
                """);
    }

    @Test
    void tc_s02_enumBecomesStringWithValues() throws Exception {
        assertSchema(EnumParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "level": {"type": "string", "enum": ["LOW", "HIGH"]}
                  }
                }
                """);
    }

    @Test
    void tc_s03_listOfScalarsBecomesArrayWithItems() throws Exception {
        assertSchema(ListParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "tags": {"type": "array", "items": {"type": "string"}}
                  }
                }
                """);
    }

    @Test
    void tc_s04_nestedObjectIsRecursive() throws Exception {
        assertSchema(NestedParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "address": {
                      "type": "object",
                      "properties": {
                        "city": {"type": "string"}
                      }
                    }
                  }
                }
                """);
    }

    @Test
    void tc_s05_requiredComesFromAnnotationOnly() throws Exception {
        assertSchema(RequiredParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "id": {"type": "string"},
                    "note": {"type": "string"}
                  },
                  "required": ["id"]
                }
                """);
    }

    @Test
    void tc_s06_noRequiredKeyWhenNothingRequired() {
        JsonNode schema = ToolSchemaFactory.generate(ScalarParams.class);

        assertFalse(schema.has("required"));
    }

    @Test
    void tc_s07_descriptionIsCopied() throws Exception {
        assertSchema(DescribedParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string", "description": "用户名称"}
                  }
                }
                """);
    }

    @Test
    void tc_s08_fieldsWithoutJsonPropertyAreSkipped() throws Exception {
        assertSchema(MixedParams.class, """
                {
                  "type": "object",
                  "properties": {
                    "kept": {"type": "string"}
                  }
                }
                """);
    }

    @Test
    void tc_s09_unsupportedTypesAreRejected() {
        assertThrows(ConfigurationException.class, () -> ToolSchemaFactory.generate(MapParams.class));
        assertThrows(ConfigurationException.class, () -> ToolSchemaFactory.generate(OptionalParams.class));
        assertThrows(ConfigurationException.class, () -> ToolSchemaFactory.generate(DateParams.class));
        assertThrows(ConfigurationException.class, () -> ToolSchemaFactory.generate(ObjectListParams.class));
    }

    @Test
    void tc_s10_selfReferenceIsRejected() {
        assertThrows(ConfigurationException.class, () -> ToolSchemaFactory.generate(SelfRefParams.class));
    }

    private void assertSchema(Class<?> type, String expectedJson) throws Exception {
        JsonNode expected = mapper.readTree(expectedJson);
        JsonNode actual = ToolSchemaFactory.generate(type);
        assertEquals(expected, actual);
    }

    enum Level {
        LOW,
        HIGH
    }

    static class ScalarParams implements ToolParameters {
        @JsonProperty("string")
        private String string;
        @JsonProperty("count")
        private int count;
        @JsonProperty("big")
        private long big;
        @JsonProperty("ratio")
        private double ratio;
        @JsonProperty("flag")
        private boolean flag;
    }

    static class EnumParams implements ToolParameters {
        @JsonProperty("level")
        private Level level;
    }

    static class ListParams implements ToolParameters {
        @JsonProperty("tags")
        private List<String> tags;
    }

    static class Address implements ToolParameters {
        @JsonProperty("city")
        private String city;
    }

    static class NestedParams implements ToolParameters {
        @JsonProperty("address")
        private Address address;
    }

    static class RequiredParams implements ToolParameters {
        @JsonProperty(value = "id", required = true)
        private String id;
        @JsonProperty("note")
        private String note;
    }

    static class DescribedParams implements ToolParameters {
        @JsonProperty("name")
        @JsonPropertyDescription("用户名称")
        private String name;
    }

    static class MixedParams implements ToolParameters {
        @JsonProperty("kept")
        private String kept;
        private String skipped;
    }

    static class MapParams implements ToolParameters {
        @JsonProperty("map")
        private Map<String, String> map;
    }

    static class OptionalParams implements ToolParameters {
        @JsonProperty("opt")
        private Optional<String> opt;
    }

    static class DateParams implements ToolParameters {
        @JsonProperty("date")
        private LocalDate date;
    }

    static class ObjectListParams implements ToolParameters {
        @JsonProperty("list")
        private List<Address> list;
    }

    static class SelfRefParams implements ToolParameters {
        @JsonProperty("child")
        private SelfRefParams child;
    }
}
