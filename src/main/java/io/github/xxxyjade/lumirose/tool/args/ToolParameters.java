package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public interface ToolParameters {

    @JsonValue
    default JsonNode toSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (Field field : getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }

            String name = field.getName();

            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty == null) {
                continue;
            }
            if (!jsonProperty.value().isEmpty()) {
                name = jsonProperty.value();
            }

            ObjectNode prop = properties.putObject(name);

            prop.put("type", jsonTypeOf(field));

            JsonPropertyDescription desc = field.getAnnotation(JsonPropertyDescription.class);
            if (desc != null && !desc.value().isEmpty()) {
                prop.put("description", desc.value());
            }

            if (jsonProperty.required()) {
                required.add(name);
            }
        }

        if (required.isEmpty()) {
            schema.remove("required");
        }

        return schema;
    }

    private static String jsonTypeOf(Field field) {
        return switch (field.getType()) {
            case Class<?> c
                when
                    c == String.class ||
                    c == char.class ||
                    c == Character.class
                -> "string";
            case Class<?> c
                when
                    c == int.class || c == Integer.class ||
                    c == long.class || c == Long.class ||
                    c == short.class || c == Short.class ||
                    c == byte.class || c == Byte.class
                -> "integer";
            case Class<?> c
                when
                    c == double.class || c == Double.class ||
                    c == float.class || c == Float.class
                -> "number";
            case Class<?> c
                when
                    c == boolean.class || c == Boolean.class
                -> "boolean";
            case Class<?> c
                when
                    c.isArray() || List.class.isAssignableFrom(c)
                -> "array";
            default -> "object";
        };
    }
}

