package org.example.ai_study_notes.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ContentType工具类，用于处理不同content-type类型的参数添加
 */
@Slf4j
public class ContentTypeUtils {

    // 改用线程安全的ConcurrentHashMap，并在构建后清理缓存
    private static final Map<String, Map<String, Object>> jsonParamCache = new ConcurrentHashMap<>();
    private static final Map<String, List<BasicNameValuePair>> formParamCache = new ConcurrentHashMap<>();
    private static final Map<String, MultipartEntityBuilder> multipartCache = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> xmlParamCache = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> textParamCache = new ConcurrentHashMap<>();

    /**
     * 处理参数添加，根据ContentType和HTTP方法进行相应处理
     * 注意：同一请求应仅调用一次此方法，避免重复构建请求体
     */
    public static void handleParam(Map<String, String> map, ContentType contentType,
                                   ClassicHttpRequest request, String method) throws Exception {
        if (map == null || map.isEmpty() || contentType == null || request == null) {
            return;
        }


        String requestId = getRequestIdentifier(request);
        String mimeType = contentType.getMimeType();

        switch (mimeType) {
            case "application/json":
                handleJsonParam(requestId, map, request, method);
                break;
            case "application/x-www-form-urlencoded":
                handleFormUrlEncodedParam(requestId, map, request, method);
                break;
            case "multipart/form-data":
                handleMultipartParam(requestId, map, request, method);
                break;
            case "application/xml":
                handleXmlParam(requestId, map, request, method);
                break;
            case "text/plain":
                handleTextPlainParam(requestId, map, request, method);
                break;
            default:
                handleJsonParam(requestId, map, request, method); // 默认按JSON处理
        }
    }


    /**
     * 处理JSON格式参数
     */
    private static void handleJsonParam(String requestId, Map<String, String> map,
                                        ClassicHttpRequest request, String method) {
        Map<String, Object> params = jsonParamCache.computeIfAbsent(requestId, k -> new HashMap<>());
        params.putAll(map); // 批量添加参数

        String jsonBody = mapToJson(params);
        setRequestEntity(request, new StringEntity(jsonBody, ContentType.APPLICATION_JSON), method);
        jsonParamCache.remove(requestId); // 构建后立即清理缓存
    }

    /**
     * 处理表单格式参数
     */
    private static void handleFormUrlEncodedParam(String requestId, Map<String, String> map,
                                                  ClassicHttpRequest request, String method) {
        List<BasicNameValuePair> params = formParamCache.computeIfAbsent(requestId, k -> new ArrayList<>());
        map.forEach((key, value) -> params.add(new BasicNameValuePair(key, value)));

        UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(params, StandardCharsets.UTF_8);
        setRequestEntity(request, formEntity, method);
        formParamCache.remove(requestId); // 清理缓存
    }


    private static void handleMultipartParam(String requestId, Map<String, String> map,
                                             ClassicHttpRequest request, String method) throws IOException {
        MultipartEntityBuilder builder = multipartCache.computeIfAbsent(requestId, k -> {
            MultipartEntityBuilder b = MultipartEntityBuilder.create();
            return b;
        });

        map.forEach((key, value) ->
                // 显式指定UTF-8编码（避免中文乱码导致400）
                builder.addTextBody(key, value, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8))
        );

        HttpEntity multipartEntity = builder.build();
        // 移除内容流打印，避免消耗请求内容
        
        // 移除请求中已存在的Content-Type头，避免与HttpClient自动生成的头冲突
        request.removeHeaders("Content-Type");
        
        setRequestEntity(request, multipartEntity, method);
        multipartCache.remove(requestId); // 清理缓存，避免参数污染
    }

    /**
     * 处理XML格式参数
     */
    private static void handleXmlParam(String requestId, Map<String, String> map,
                                       ClassicHttpRequest request, String method) {
        Map<String, Object> params = xmlParamCache.computeIfAbsent(requestId, k -> new HashMap<>());
        params.putAll(map);

        String xmlBody = mapToXml(params);
        setRequestEntity(request, new StringEntity(xmlBody, ContentType.APPLICATION_XML), method);
        xmlParamCache.remove(requestId); // 清理缓存
    }

    /**
     * 处理纯文本参数
     */
    private static void handleTextPlainParam(String requestId, Map<String, String> map,
                                             ClassicHttpRequest request, String method) {
        StringBuilder textBuilder = textParamCache.computeIfAbsent(requestId, k -> new StringBuilder());

        map.forEach((key, value) -> {
            if (textBuilder.length() > 0) {
                textBuilder.append("\n");
            }
            textBuilder.append(key).append(": ").append(value);
        });

        setRequestEntity(request, new StringEntity(textBuilder.toString(), ContentType.TEXT_PLAIN), method);
        textParamCache.remove(requestId); // 清理缓存
    }

    /**
     * 设置请求实体，确保实体仅被设置一次
     */
    private static void setRequestEntity(ClassicHttpRequest request, HttpEntity entity, String method) {
        // 避免重复设置实体（防止覆盖）
        if (request instanceof HttpPost && ((HttpPost) request).getEntity() == null) {
            ((HttpPost) request).setEntity(entity);
        } else if (request instanceof HttpPut && ((HttpPut) request).getEntity() == null) {
            ((HttpPut) request).setEntity(entity);
        } else if (request instanceof HttpPatch && ((HttpPatch) request).getEntity() == null) {
            ((HttpPatch) request).setEntity(entity);
        }
    }

    /**
     * 将Map转换为JSON字符串（使用更可靠的拼接逻辑）
     */
    private static String mapToJson(Map<String, Object> params) {
        if (params.isEmpty()) {
            return "{}";
        }
        StringBuilder jsonBuilder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\"").append(escapeJson(entry.getKey())).append("\":")
                    .append("\"").append(escapeJson(entry.getValue().toString())).append("\"");
            first = false;
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    /**
     * 将Map转换为XML字符串
     */
    private static String mapToXml(Map<String, Object> params) {
        if (params.isEmpty()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root/>";
        }
        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n");
        params.forEach((key, value) -> xmlBuilder.append("  <")
                .append(escapeXml(key))
                .append(">")
                .append(escapeXml(value.toString()))
                .append("</")
                .append(escapeXml(key))
                .append(">\n"));
        xmlBuilder.append("</root>");
        return xmlBuilder.toString();
    }

    /**
     * JSON字符串转义
     */
    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * XML字符串转义
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 获取请求的唯一标识符（使用UUID确保绝对唯一）
     */
    private static String getRequestIdentifier(ClassicHttpRequest request) {
        // 结合请求对象哈希和UUID，避免多线程场景下的ID冲突
        return System.identityHashCode(request) + "_" +
                request.getRequestUri() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}