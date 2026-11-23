package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.tool.impl.EditTool;
import io.github.xxxyjade.lumirose.tool.impl.ReadTool;
import io.github.xxxyjade.lumirose.tool.impl.WriteTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 由参数类生成 JSON Schema 的类型映射、required/description 与拒绝分支测试。
 */
class SchemaTest {

    @Test
    void rootIsAlwaysObjectSchema() {
        JsonNode schema = ToolSchemaFactory.generate(EmptyParameters.class);

        assertEquals("object", schema.get("type").asText());
        assertEquals(0, schema.get("properties").size());
        assertFalse(schema.has("required"));
    }

    @Test
    void mapsScalarTypes() {
        JsonNode properties = ToolSchemaFactory.generate(Scalars.class).get("properties");

        assertEquals("string", properties.get("text").get("type").asText());
        assertEquals("string", properties.get("letter").get("type").asText());
        assertEquals("integer", properties.get("count").get("type").asText());
        assertEquals("integer", properties.get("bigCount").get("type").asText());
        assertEquals("integer", properties.get("smallCount").get("type").asText());
        assertEquals("number", properties.get("ratio").get("type").asText());
        assertEquals("number", properties.get("amount").get("type").asText());
        assertEquals("boolean", properties.get("enabled").get("type").asText());
    }

    @Test
    void mapsEnumToNamedStringValues() {
        JsonNode color = ToolSchemaFactory.generate(WithEnum.class).get("properties").get("color");

        assertEquals("string", color.get("type").asText());
        assertEquals(2, color.get("enum").size());
        assertEquals("RED", color.get("enum").get(0).asText());
        assertEquals("GREEN", color.get("enum").get(1).asText());
    }

    @Test
    void mapsScalarCollections() {
        JsonNode properties = ToolSchemaFactory.generate(WithCollections.class).get("properties");

        JsonNode tags = properties.get("tags");
        assertEquals("array", tags.get("type").asText());
        assertEquals("string", tags.get("items").get("type").asText());

        JsonNode colors = properties.get("colors");
        assertEquals("array", colors.get("type").asText());
        assertEquals("RED", colors.get("items").get("enum").get(0).asText());

        JsonNode scores = properties.get("scores");
        assertEquals("array", scores.get("type").asText());
        assertEquals("integer", scores.get("items").get("type").asText());
    }

    @Test
    void mapsObjectArraysOfScalars() {
        JsonNode values = ToolSchemaFactory.generate(WithArray.class).get("properties").get("values");

        assertEquals("array", values.get("type").asText());
        assertEquals("integer", values.get("items").get("type").asText());
    }

    @Test
    void requiredOnlyComesFromAnnotation() {
        JsonNode schema = ToolSchemaFactory.generate(WithMixedRequired.class);
        JsonNode required = schema.get("required");

        assertEquals(1, required.size());
        assertEquals("needed", required.get(0).asText());
        assertTrue(schema.get("properties").has("optional"));
    }

    @Test
    void includesDescriptionAndJsonPropertyName() {
        JsonNode schema = ToolSchemaFactory.generate(WithDescription.class);
        JsonNode filePath = schema.get("properties").get("file_path");

        assertEquals("string", filePath.get("type").asText());
        assertEquals("相对于工作区的文件路径", filePath.get("description").asText());
        assertFalse(schema.get("properties").has("path"));
    }

    @Test
    void ignoresStaticSyntheticAndUnannotatedFields() {
        JsonNode schema = ToolSchemaFactory.generate(WithIgnoredFields.class);

        assertEquals(1, schema.get("properties").size());
        assertTrue(schema.get("properties").has("kept"));
        assertFalse(schema.get("properties").has("unannotated"));
        assertFalse(schema.get("properties").has("staticField"));
    }

    @Test
    void mapsNestedObjectParameter() {
        JsonNode inner = ToolSchemaFactory.generate(WithNested.class)
                .get("properties").get("inner");

        assertEquals("object", inner.get("type").asText());
        assertEquals("string", inner.get("properties").get("name").get("type").asText());
    }

    @Test
    void rejectsNullType() {
        assertConfigInvalid(() -> ToolSchemaFactory.generate(null));
    }

    @Test
    void rejectsUnsupportedContainersAndTypes() {
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithMap.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithSet.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithOptional.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithObject.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithRawList.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithObjectList.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithObjectArray.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithDate.class));
        assertConfigInvalid(() -> ToolSchemaFactory.generate(WithLocalDateTime.class));
    }

    @Test
    void rejectsRecursiveParameterTypes() {
        assertConfigInvalid(() -> ToolSchemaFactory.generate(RecursiveNode.class));
    }

    @Test
    void fileToolSchemasExposeTheirParameters() {
        JsonNode read = ToolSchemaFactory.generate(ReadTool.Params.class);
        assertTrue(requiredNames(read).contains("path"));

        JsonNode write = ToolSchemaFactory.generate(WriteTool.Params.class);
        assertTrue(requiredNames(write).containsAll(List.of("path", "content")));
        assertFalse(requiredNames(write).contains("overwrite"));

        JsonNode edit = ToolSchemaFactory.generate(EditTool.Params.class);
        assertTrue(requiredNames(edit).containsAll(List.of("path", "old_string", "new_string")));
    }

    private static List<String> requiredNames(JsonNode schema) {
        JsonNode required = schema.get("required");
        return required == null ? List.of() : java.util.stream.StreamSupport
                .stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private static void assertConfigInvalid(Executable executable) {
        LumiRoseException error = assertThrows(LumiRoseException.class, executable);
        assertEquals(LumiRoseErrorCode.CONFIG_INVALID, error.getErrorCode());
    }

    public enum Color {
        RED, GREEN
    }

    public static final class Scalars implements ToolParameters {
        @JsonProperty
        public String text;
        @JsonProperty
        public char letter;
        @JsonProperty
        public int count;
        @JsonProperty
        public long bigCount;
        @JsonProperty
        public short smallCount;
        @JsonProperty
        public double ratio;
        @JsonProperty
        public BigDecimal amount;
        @JsonProperty
        public boolean enabled;
    }

    public static final class WithEnum implements ToolParameters {
        @JsonProperty
        public Color color;
    }

    public static final class WithCollections implements ToolParameters {
        @JsonProperty
        public List<String> tags;
        @JsonProperty
        public List<Color> colors;
        @JsonProperty
        public List<Integer> scores;
    }

    public static final class WithArray implements ToolParameters {
        @JsonProperty
        public int[] values;
    }

    public static final class WithMixedRequired implements ToolParameters {
        @JsonProperty(required = true)
        public String needed;
        @JsonProperty
        public String optional;
    }

    public static final class WithDescription implements ToolParameters {
        @JsonProperty("file_path")
        @JsonPropertyDescription("相对于工作区的文件路径")
        public String path;
    }

    public static final class WithIgnoredFields implements ToolParameters {
        @JsonProperty
        public static String staticField;
        @JsonProperty
        public String kept;
        public String unannotated;
    }

    public static final class WithNested implements ToolParameters {
        @JsonProperty
        public Inner inner;
    }

    public static final class Inner implements ToolParameters {
        @JsonProperty
        public String name;
    }

    public static final class WithMap implements ToolParameters {
        @JsonProperty
        public Map<String, String> values;
    }

    public static final class WithSet implements ToolParameters {
        @JsonProperty
        public Set<String> values;
    }

    public static final class WithOptional implements ToolParameters {
        @JsonProperty
        public Optional<String> value;
    }

    public static final class WithObject implements ToolParameters {
        @JsonProperty
        public Object value;
    }

    public static final class WithRawList implements ToolParameters {
        @JsonProperty
        @SuppressWarnings("rawtypes")
        public List values;
    }

    public static final class WithObjectList implements ToolParameters {
        @JsonProperty
        public List<Object> values;
    }

    public static final class WithObjectArray implements ToolParameters {
        @JsonProperty
        public Object[] values;
    }

    public static final class WithDate implements ToolParameters {
        @JsonProperty
        public Date when;
    }

    public static final class WithLocalDateTime implements ToolParameters {
        @JsonProperty
        public LocalDateTime when;
    }

    public static final class RecursiveNode implements ToolParameters {
        @JsonProperty
        public RecursiveNode next;
    }
}
