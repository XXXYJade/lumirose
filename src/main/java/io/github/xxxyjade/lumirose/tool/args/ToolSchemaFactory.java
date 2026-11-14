package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 由参数类反射生成工具参数的 JSON Schema。
 */
final class ToolSchemaFactory {

    private ToolSchemaFactory() {
    }

    static JsonNode generate(Class<?> type) {
        return objectSchema(type, new HashSet<>());
    }

    private static ObjectNode objectSchema(Class<?> type, Set<Class<?>> visiting) {
        if (!visiting.add(type)) {
            throw invalid("不支持的参数类型（递归引用）: " + type.getName());
        }
        try {
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = JsonNodeFactory.instance.arrayNode();

            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                if (jsonProperty == null) {
                    continue;
                }

                String name = jsonProperty.value().isEmpty() ? field.getName() : jsonProperty.value();
                ObjectNode property = fieldSchema(field, visiting);

                JsonPropertyDescription description = field.getAnnotation(JsonPropertyDescription.class);
                if (description != null && !description.value().isEmpty()) {
                    property.put("description", description.value());
                }

                properties.set(name, property);
                if (jsonProperty.required()) {
                    required.add(name);
                }
            }

            if (!required.isEmpty()) {
                schema.set("required", required);
            }
            return schema;
        } finally {
            visiting.remove(type);
        }
    }

    private static ObjectNode fieldSchema(Field field, Set<Class<?>> visiting) {
        Class<?> type = field.getType();

        if (type == String.class || type == char.class || type == Character.class) {
            return scalar("string");
        }
        if (isInteger(type)) {
            return scalar("integer");
        }
        if (isNumber(type)) {
            return scalar("number");
        }
        if (type == boolean.class || type == Boolean.class) {
            return scalar("boolean");
        }
        if (type.isEnum()) {
            return enumSchema(type);
        }
        if (List.class.isAssignableFrom(type)) {
            return arraySchema(listItemType(field));
        }
        if (type.isArray()) {
            return arraySchema(type.getComponentType());
        }
        if (unsupported(type)) {
            throw invalid("不支持的参数类型: " + type.getName());
        }
        return objectSchema(type, visiting);
    }

    private static ObjectNode arraySchema(Class<?> itemType) {
        if (unsupported(itemType) || (!isScalar(itemType) && !itemType.isEnum())) {
            throw invalid("不支持的参数类型: List<" + itemType.getName() + ">");
        }
        ObjectNode schema = scalar("array");
        schema.set("items", itemType.isEnum() ? enumSchema(itemType) : scalar(jsonTypeOf(itemType)));
        return schema;
    }

    private static ObjectNode enumSchema(Class<?> type) {
        ObjectNode schema = scalar("string");
        ArrayNode values = schema.putArray("enum");
        for (Object constant : type.getEnumConstants()) {
            values.add(((Enum<?>) constant).name());
        }
        return schema;
    }

    private static ObjectNode scalar(String type) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", type);
        return schema;
    }

    private static String jsonTypeOf(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class) {
            return "string";
        }
        if (isInteger(type)) {
            return "integer";
        }
        if (isNumber(type)) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        throw invalid("不支持的参数类型: " + type.getName());
    }

    private static boolean isScalar(Class<?> type) {
        return type == String.class
                || type == char.class || type == Character.class
                || isInteger(type) || isNumber(type)
                || type == boolean.class || type == Boolean.class;
    }

    private static boolean isInteger(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == BigInteger.class;
    }

    private static boolean isNumber(Class<?> type) {
        return type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == BigDecimal.class;
    }

    private static Class<?> listItemType(Field field) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            throw invalid("List 必须声明元素类型: " + field.getName());
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        if (!(argument instanceof Class<?> itemType)) {
            throw invalid("List 元素类型不受支持: " + field.getName());
        }
        return itemType;
    }

    private static boolean unsupported(Class<?> type) {
        return type == Object.class
                || Map.class.isAssignableFrom(type)
                || (Collection.class.isAssignableFrom(type) && !List.class.isAssignableFrom(type))
                || type == Optional.class
                || Date.class.isAssignableFrom(type)
                || Calendar.class.isAssignableFrom(type)
                || type.getPackageName().startsWith("java.time");
    }

    private static ConfigurationException invalid(String message) {
        return new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, message);
    }
}
