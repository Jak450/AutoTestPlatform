package org.example.ai_study_notes.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;


import java.io.IOException;

import java.util.Collection;
import java.util.Map;

@Slf4j
public class JsonUtils {

    private static  final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {}

    /*
    将JSON字符串解析为指定对象
     */
    public static <T> T parse(String json, Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readValue(json, clazz);
    }

    /*
    将JSON字符串解析为MAP对象
     */
    public static Map<String, Object> parseToMap(String json) throws IOException {
        return OBJECT_MAPPER.readValue(json, Map.class);
    }

    public static Object getValueFromMap(Map<String, Object> map, String key) {
        String[] parts = key.split("\\.");
        Object value = map;
        for(String part : parts) {

            if(value instanceof Map) {
                value = ((Map<?,?>) value).get(part);
                if (value == null)
                {
                    break;
                }
            }
            else {
                return null;
            }
        }
        return value;
    }




    public static String serializeForReport(Object obj) {
        String json = null;
        // 手动判断obj是Collection还是Map，分别处理
        boolean isEmpty = true;
        if (obj instanceof Collection) {
            isEmpty = CollectionUtils.isEmpty((Collection<?>) obj);
        } else if (obj instanceof Map) {
            isEmpty = CollectionUtils.isEmpty((Map<?, ?>) obj);
        }
        // 非空时执行序列化
        if (!isEmpty) {
            try {
                json = OBJECT_MAPPER.writeValueAsString(obj);
            } catch (JsonProcessingException e) {
                log.warn("序列化响应体失败", e);
                json = obj.toString();
            }
        }
        return json;
    }


}
