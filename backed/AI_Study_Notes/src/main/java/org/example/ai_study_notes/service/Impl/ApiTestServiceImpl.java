package org.example.ai_study_notes.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;

import org.example.ai_study_notes.Pojo.vo.AssertResult;
import org.example.ai_study_notes.service.ApiTestService;
import org.example.ai_study_notes.service.UseCaseService;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.utils.ContentTypeUtils;
import org.example.ai_study_notes.utils.JsonUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ApiTestServiceImpl implements ApiTestService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private UseCaseService useCaseService;
    
    @Autowired
    @Qualifier("testExecutor")
    private Executor apiTestExecutor;
    
    // 共享的HttpClient实例，支持连接复用
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    @Override
    public ApiResponseVO run(ApiRequestDTO apiRequestDTO) {
        // 初始化响应对象
        ApiResponseVO responseVO = new ApiResponseVO();
        List<AssertResult> assertResults = new ArrayList<>();
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, String> headersMap = new HashMap<>();

        CloseableHttpResponse httpResponse = null;
        long startTime = Instant.now().toEpochMilli();

        try {
            // 使用共享的HttpClient实例（支持连接复用，提高并发性能）
            // 创建上下文对象
            HttpClientContext context = HttpClientContext.create();
            
            // 根据请求方法创建相应的Http请求对象
            ClassicHttpRequest httpRequest = createHttpRequest(apiRequestDTO);
            
            // 添加请求头
         ContentType contentType=addHeaders(httpRequest, apiRequestDTO.getHeader());
            
            // 添加请求参数
            addParams(httpRequest, apiRequestDTO,contentType);
            
            // 发送请求并获取响应 - 使用推荐的API
            httpResponse = httpClient.execute(httpRequest, context);





            // 计算响应时间
            long endTime = Instant.now().toEpochMilli();
            long responseTime = endTime - startTime;
            
            // 处理响应状态码
            int statusCode = httpResponse.getCode();
            responseVO.setStatus(statusCode);
            
            // 处理响应头 - 修复Header[]不能直接forEach的问题
            Header[] headers = httpResponse.getHeaders();
            for (Header header : headers) {
                headersMap.put(header.getName(), header.getValue());
            }
            responseVO.setHeaders(headersMap);
            
            // 处理响应体
            HttpEntity entity = httpResponse.getEntity();
            String responseBody = "";
            if (entity != null) {
                responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            }
            responseVO.setSize((long) responseBody.length());
            responseVO.setTime(responseTime);
            
            // 尝试将响应体解析为JSON
            try {
                Map<String, Object> responseJson = JsonUtils.parseToMap(responseBody);
                resultMap.putAll(responseJson);
            } catch (Exception e) {
                // 如果不是JSON格式，直接存储字符串
                resultMap.put("response", responseBody);
            }
            responseVO.setResult(resultMap);
            
            // 执行断言
            if (apiRequestDTO.getAssertStr() != null && !apiRequestDTO.getAssertStr().trim().isEmpty()) {
                assertResults = executeAsserts(resultMap, apiRequestDTO.getAssertStr());
            }
            responseVO.setAssertResults(assertResults);
            
        } catch (Exception e) {
            log.error("API 测试执行异常", e);
            // 处理异常情况
            resultMap.put("error", e.getMessage());
            responseVO.setResult(resultMap);
            responseVO.setStatus(500);
            AssertResult assertResult = AssertResult.builder()
                    .field("system")
                    .expected("success")
                    .actual("error: " + e.getMessage())
                    .result(false)
                    .build();
            assertResults.add(assertResult);
            responseVO.setAssertResults(assertResults);
        } finally {
            // 关闭资源
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    log.error("关闭 HTTP 响应失败", e);
                }
            }
            // 注意：不再关闭共享的httpClient，它会在应用关闭时自动关闭
        }
        
        return responseVO;
    }
    
    /**
     * 根据请求方法创建对应的HTTP请求对象
     */
    private ClassicHttpRequest createHttpRequest(ApiRequestDTO apiRequestDTO) {
        if (apiRequestDTO.getMethod() == null) {
            throw new IllegalArgumentException("请求方法不能为空！请提供一个完整的HTTP方法(GET, POST, PUT, DELETE, PATCH).");
        }
        String method = apiRequestDTO.getMethod().toUpperCase();
        
        if (apiRequestDTO.getUrl() == null || apiRequestDTO.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空，请提供一个完整的URL.");
        }
        String url = apiRequestDTO.getUrl();
        
        switch (method) {
            case "GET":
                return new HttpGet(url);
            case "POST":
                return new HttpPost(url);
            case "PUT":
                return new HttpPut(url);
            case "DELETE":
                return new HttpDelete(url);
            case "PATCH":
                return new HttpPatch(url);
            default:
                throw new IllegalArgumentException("不支持该请求方法: " + method);
        }
    }
    
    /**
     * 添加请求头
     */
    private ContentType addHeaders(ClassicHttpRequest request, String headerStr) {
        if (headerStr == null || headerStr.trim().isEmpty()) {
            return ContentType.APPLICATION_JSON;
        }

        ContentType contentType = ContentType.APPLICATION_JSON;
        
        try {
            // 假设header是JSON格式
            Map<String, String> headers = convertWithCustomRule(JsonUtils.parseToMap(headerStr));
            headers.forEach(request::addHeader);
            // 从JSON格式中查找Content-Type
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                    contentType = parseContentType(entry.getValue());
                    break;
                }
            }
        } catch (Exception e) {
            // 尝试按行解析
            String[] lines = headerStr.split("\\n");
            for (String line : lines) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String name = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    request.addHeader(name, value);
                    // 判断是否是Content-Type
                    if ("Content-Type".equalsIgnoreCase(name)) {
                        contentType = parseContentType(value);
                    }
                }
            }
        }
        return contentType;
    }
    
    /**
     * 针对不同请求方法而处理不同的请求体或请求参数
     */
    private void addParams(ClassicHttpRequest request, ApiRequestDTO apiRequestDTO,ContentType contentType) throws Exception {
        String param = apiRequestDTO.getParam();
        if (param == null || param.trim().isEmpty()) {
            return;
        }
        String method = apiRequestDTO.getMethod().toUpperCase();
        // 对于GET请求，参数应该已经包含在URL中
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return ;
        }

        Map<String, String> paramsMap = objectMapper.readValue(param, Map.class);
       ContentTypeUtils.handleParam(paramsMap,contentType,request,method);

    }
    
    /**
     * 执行断言逻辑
     */
    private List<AssertResult> executeAsserts(Map<String, Object> responseData, String assertStr) {
        List<AssertResult> results = new ArrayList<>();
        
        // 假设assertStr是JSON格式的断言配置
        try {
            Map<String, Object> asserts = JsonUtils.parseToMap(assertStr);
            
            for (Map.Entry<String, Object> entry : asserts.entrySet()) {
                String field = entry.getKey();
                Object expected = entry.getValue();
                
                // 获取实际值
                Object actual = JsonUtils.getValueFromMap(responseData, field);
                
                // 比较
                boolean match = compareValues(expected, actual);
                
                AssertResult assertResult = AssertResult.builder()
                        .field(field)
                        .expected(expected)
                        .actual(actual)
                        .result(match)
                        .build();
                
                results.add(assertResult);
            }
        } catch (Exception e) {
            // 如果断言配置不是JSON格式，尝试简单的字符串匹配
            AssertResult assertResult = AssertResult.builder()
                    .field("response")
                    .expected(assertStr)
                    .actual(responseData.toString())
                    .result(responseData.toString().contains(assertStr))
                    .build();
            results.add(assertResult);
        }
        
        return results;
    }
    

    
    /**
     * 比较期望值和实际值
     */
    private boolean compareValues(Object expected, Object actual) {
        if (expected == null && actual == null) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }
        
        // 处理数字比较
        if (expected instanceof Number && actual instanceof Number) {
            double expectedDouble = ((Number) expected).doubleValue();
            double actualDouble = ((Number) actual).doubleValue();
            return Math.abs(expectedDouble - actualDouble) < 0.0001;
        }
        
        // 处理字符串比较
        if (expected instanceof String && actual instanceof String) {
            // 检查是否是正则表达式
            String expectedStr = (String) expected;
            if (expectedStr.startsWith("regex:") && expectedStr.length() > 6) {
                String regex = expectedStr.substring(6);
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher((String) actual);
                return matcher.find();
            }
        }
        
        // 默认使用equals方法比较
        return expected.equals(actual);
    }

    public static Map<String, String> convertWithCustomRule(Map<String, Object> objectMap) {
        Map<String, String> stringMap = new HashMap<>();
        if (objectMap == null || objectMap.isEmpty()) {
            return stringMap;
        }

        // 自定义日期格式（避免Date直接toString()返回非预期字符串）
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String stringValue;

            // 针对不同类型做自定义转换
            if (value == null) {
                stringValue = null;
            } else if (value instanceof Date) {
                stringValue = dateFormat.format((Date) value); // 日期按指定格式转换
            } else if (value instanceof Number) {
                stringValue = value.toString(); // 数字直接转字符串（如123→"123"）
            } else if (value instanceof Boolean) {
                stringValue = value.toString(); // 布尔值→"true"/"false"
            } else {
                // 其他类型默认调用toString()
                stringValue = value.toString();
            }

            stringMap.put(key, stringValue);
        }
        return stringMap;
    }
    
    /**
     * 批量并发执行接口测试
     */
    @Override
    public BatchExecuteResultVO batchExecute(BatchExecuteDTO batchExecuteDTO) {
        long startTime = System.currentTimeMillis();
        
        List<Integer> useCaseIds = batchExecuteDTO.getUseCaseIds();
        Integer executionCount = batchExecuteDTO.getExecutionCount() != null ? batchExecuteDTO.getExecutionCount() : 1;
        Integer maxConcurrency = batchExecuteDTO.getMaxConcurrency() != null ? batchExecuteDTO.getMaxConcurrency() : 10;
        
        List<BatchExecuteResultVO.ExecuteDetailVO> details = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        
        // 创建信号量控制并发数
        Semaphore semaphore = new Semaphore(maxConcurrency);
        
        // 批量查询所有用例信息，减少数据库查询次数（性能优化）
        Map<Integer, UseCase> useCaseMap = new HashMap<>();
        for (Integer useCaseId : useCaseIds) {
            UseCase useCase = useCaseService.getUseCasesById(useCaseId);
            if (useCase != null) {
                useCaseMap.put(useCaseId, useCase);
            }
        }
        
        // 构建所有需要执行的任务
        List<CompletableFuture<BatchExecuteResultVO.ExecuteDetailVO>> futures = new ArrayList<>();
        
        for (Integer useCaseId : useCaseIds) {
            UseCase useCase = useCaseMap.get(useCaseId);
            if (useCase == null) {
                continue;
            }
            
            // 每个用例执行指定次数
            for (int i = 0; i < executionCount; i++) {
                final int executionIndex = i + 1;
                final UseCase finalUseCase = useCase;
                
                CompletableFuture<BatchExecuteResultVO.ExecuteDetailVO> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // 获取信号量许可（控制并发数）
                        semaphore.acquire();
                        
                        long taskStartTime = System.currentTimeMillis();
                        
                        // 执行接口测试
                        ApiRequestDTO apiRequestDTO = new ApiRequestDTO();
                        BeanUtils.copyProperties(finalUseCase, apiRequestDTO);
                        ApiResponseVO responseVO = run(apiRequestDTO);
                        
                        long taskDuration = System.currentTimeMillis() - taskStartTime;
                        
                        // 判断是否成功
                        boolean isSuccess = responseVO.getStatus() >= 200 && responseVO.getStatus() < 300;
                        
                        return BatchExecuteResultVO.ExecuteDetailVO.builder()
                                .useCaseId(finalUseCase.getId())
                                .useCaseName(finalUseCase.getName())
                                .executionIndex(executionIndex)
                                .result(responseVO)
                                .success(isSuccess)
                                .duration(taskDuration)
                                .build();
                    } catch (Exception e) {
                        log.error("批量执行单条用例失败", e);
                        return BatchExecuteResultVO.ExecuteDetailVO.builder()
                                .useCaseId(finalUseCase.getId())
                                .useCaseName(finalUseCase.getName())
                                .executionIndex(executionIndex)
                                .result(null)
                                .success(false)
                                .duration(0L)
                                .build();
                    } finally {
                        // 释放信号量许可
                        semaphore.release();
                    }
                }, apiTestExecutor);
                
                futures.add(future);
            }
        }
        
        // 等待所有任务完成并收集结果
        List<CompletableFuture<BatchExecuteResultVO.ExecuteDetailVO>> allFutures = 
                futures.stream().map(f -> f.exceptionally(ex -> {
                    log.error("API 批量执行任务异常", ex);
                    // 异常情况下返回失败结果（内部已处理异常，这里只是兜底）
                    return BatchExecuteResultVO.ExecuteDetailVO.builder()
                            .success(false)
                            .duration(0L)
                            .result(null)
                            .build();
                })).collect(Collectors.toList());
        
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                allFutures.toArray(new CompletableFuture[0])
        );
        
        // 等待所有任务完成
        allTasks.join();
        
        // 收集结果
        for (CompletableFuture<BatchExecuteResultVO.ExecuteDetailVO> future : allFutures) {
            try {
                BatchExecuteResultVO.ExecuteDetailVO detail = future.get();
                details.add(detail);
                if (detail.getSuccess() != null && detail.getSuccess()) {
                    successCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("批量执行汇总失败", e);
                failedCount++;
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        return BatchExecuteResultVO.builder()
                .total(details.size())
                .success(successCount)
                .failed(failedCount)
                .totalTime(totalTime)
                .details(details)
                .build();
    }


    private ContentType parseContentType(String contentTypeStr) {
        if (contentTypeStr == null || contentTypeStr.trim().isEmpty()) {
            return ContentType.APPLICATION_JSON;
        }

        // 特殊处理multipart/form-data，保留boundary信息
        if (contentTypeStr.toLowerCase().startsWith("multipart/form-data")) {
            try {
                return ContentType.parse(contentTypeStr);
            } catch (Exception e) {
                return ContentType.MULTIPART_FORM_DATA;
            }
        }

        // 移除可能的分号后的参数（如charset=utf-8）
        String mainType = contentTypeStr.split(";")[0].trim().toLowerCase();

        switch (mainType) {
            case "application/json":
                return ContentType.APPLICATION_JSON;
            case "application/xml":
                return ContentType.APPLICATION_XML;
            case "text/plain":
                return ContentType.TEXT_PLAIN;
            case "text/html":
                return ContentType.TEXT_HTML;
            case "application/x-www-form-urlencoded":
                return ContentType.APPLICATION_FORM_URLENCODED;
            default:
                // 对于不认识的类型，尝试创建自定义ContentType
                try {
                    return ContentType.create(mainType);
                } catch (Exception e) {
                    return ContentType.APPLICATION_JSON; // 默认回退
                }
        }
    }


}
