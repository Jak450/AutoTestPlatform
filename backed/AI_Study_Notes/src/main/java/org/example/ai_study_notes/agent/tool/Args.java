package org.example.ai_study_notes.agent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具参数解析辅助类。
 */
public final class Args {

    private Args() {
    }

    public static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Integer integer(Map<String, Object> args, String key, Integer defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 必须是整数");
        }
    }

    public static Boolean bool(Map<String, Object> args, String key, Boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static List<Integer> integerList(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                result.add(((Number) item).intValue());
            }
            return result;
        }
        throw new IllegalArgumentException("参数 " + key + " 必须是整数数组");
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listOfMaps(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        throw new IllegalArgumentException("参数 " + key + " 必须是对象数组");
    }
}
