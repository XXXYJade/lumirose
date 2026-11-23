package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.common.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 由 {@link ToolParameters} 实现类的字段生成 JSON Schema。
 *
 * <p>支持的类型：字符串与字符、整数、浮点数与 {@link BigDecimal}、布尔、
 * 枚举、标量数组、{@code List<标量>}，以及嵌套的参数对象。字段必须标注
 * {@link JsonProperty} 才会出现在 schema 中；{@code required} 只来自
 * {@code @JsonProperty(required = true)}，说明来自 {@link JsonPropertyDescription}。
 *
 * <p>为避免生成模型无法可靠填写的结构，{@code Map}、{@code Set}、
 * {@code Optional}、裸 {@code Object}、未声明元素类型的集合、对象数组、
 * 日期时间类型以及递归参数类型都会被拒绝。
 */
public final class ToolSchemaFactory {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private ToolSchemaFactory() {
    }

    /**
     * 生成参数类的根 schema，根节点始终是 {@code object}。
     *
     * @param parametersType 参数类型
     * @return JSON Schema 根节点
     * @throws ConfigurationException 类型为空或包含不支持的字段类型
     */
    public static JsonNode generate(Class<?> parametersType) {
        if (parametersType == null) {
            throw invalid("参数类型不能为空");
        }
        return objectSchema(parametersType, new HashSet<>());
    }

    private static ObjectNode objectSchema(Class<?> type, Set<Class<?>> stack) {
        // stack 记录当前递归路径：类型再次出现说明参数结构自引用或互相引用。
        if (!stack.add(type)) {
            throw invalid("参数类型不能递归引用");
        }
        ObjectNode schema = NODES.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = NODES.arrayNode();
        for (Field field : type.getDeclaredFields()) {
            // static 常量与编译器生成的字段（如内部类引用）不是模型参数。
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty == null) {
                // 未标注的字段既不暴露给模型，也不参与反序列化约定。
                continue;
            }
            String name = jsonProperty.value().isEmpty() ? field.getName() : jsonProperty.value();
            ObjectNode fieldSchema = fieldSchema(field.getType(), field.getGenericType(), stack);
            JsonPropertyDescription description = field.getAnnotation(JsonPropertyDescription.class);
            if (description != null && !description.value().isEmpty()) {
                fieldSchema.put("description", description.value());
            }
            properties.set(name, fieldSchema);
            if (jsonProperty.required()) {
                required.add(name);
            }
        }
        stack.remove(type);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private static ObjectNode fieldSchema(Class<?> raw, Type generic, Set<Class<?>> stack) {
        if (raw == String.class || raw == char.class || raw == Character.class) {
            return stringSchema();
        }
        if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                || raw == short.class || raw == Short.class || raw == byte.class || raw == Byte.class) {
            ObjectNode node = NODES.objectNode();
            node.put("type", "integer");
            return node;
        }
        if (raw == float.class || raw == Float.class || raw == double.class || raw == Double.class
                || raw == BigDecimal.class) {
            ObjectNode node = NODES.objectNode();
            node.put("type", "number");
            return node;
        }
        if (raw == boolean.class || raw == Boolean.class) {
            ObjectNode node = NODES.objectNode();
            node.put("type", "boolean");
            return node;
        }
        if (raw.isEnum()) {
            return enumSchema(raw);
        }
        if (raw.isArray()) {
            Class<?> component = raw.getComponentType();
            if (!isScalar(component)) {
                throw invalid("不支持对象数组");
            }
            ObjectNode node = NODES.objectNode();
            node.put("type", "array");
            node.set("items", scalarSchema(component));
            return node;
        }
        if (Map.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw)
                || Optional.class.isAssignableFrom(raw) || raw == Object.class) {
            throw invalid("不支持的参数类型: " + raw.getName());
        }
        if (List.class.isAssignableFrom(raw)) {
            if (!(generic instanceof ParameterizedType parameterized)) {
                throw invalid("集合必须声明元素类型");
            }
            Type argument = parameterized.getActualTypeArguments()[0];
            // 只允许标量元素：模型对嵌套对象数组的填写质量不可控。
            if (!(argument instanceof Class<?> element) || !isScalar(element)) {
                throw invalid("只支持标量集合");
            }
            ObjectNode node = NODES.objectNode();
            node.put("type", "array");
            node.set("items", scalarSchema(element));
            return node;
        }
        if (Temporal.class.isAssignableFrom(raw) || Date.class.isAssignableFrom(raw)
                || raw.getName().startsWith("java.sql.")) {
            throw invalid("不支持日期时间参数");
        }
        if (raw.isPrimitive() || raw.getName().startsWith("java.")) {
            // 其余 JDK 类型没有稳定的 JSON 形状，宁可配置期失败。
            throw invalid("不支持的参数类型: " + raw.getName());
        }
        return objectSchema(raw, stack);
    }

    private static boolean isScalar(Class<?> type) {
        return type == String.class || type == char.class || type == Character.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == BigDecimal.class
                || type == boolean.class || type == Boolean.class
                || type.isEnum();
    }

    private static ObjectNode scalarSchema(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class) {
            return stringSchema();
        }
        if (type.isEnum()) {
            return enumSchema(type);
        }
        if (type == boolean.class || type == Boolean.class) {
            ObjectNode node = NODES.objectNode();
            node.put("type", "boolean");
            return node;
        }
        if (type == float.class || type == Float.class || type == double.class || type == Double.class
                || type == BigDecimal.class) {
            ObjectNode node = NODES.objectNode();
            node.put("type", "number");
            return node;
        }
        ObjectNode node = NODES.objectNode();
        node.put("type", "integer");
        return node;
    }

    private static ObjectNode stringSchema() {
        ObjectNode node = NODES.objectNode();
        node.put("type", "string");
        return node;
    }

    private static ObjectNode enumSchema(Class<?> type) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "string");
        ArrayNode values = node.putArray("enum");
        for (Object constant : type.getEnumConstants()) {
            values.add(((Enum<?>) constant).name());
        }
        return node;
    }

    private static ConfigurationException invalid(String message) {
        return new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, message);
    }
}
