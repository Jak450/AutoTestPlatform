package org.example.ai_study_notes.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Aop.anno.ApiTest;
import org.example.ai_study_notes.Pojo.dto.BatchDTO;
import org.example.ai_study_notes.Pojo.dto.ReportQueryDTO;
import org.example.ai_study_notes.Pojo.entity.TestCaseReport;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.mapper.TestCaseReportMapper;
import org.example.ai_study_notes.service.TestReportService;
import org.example.ai_study_notes.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 测试报告服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestReportServiceImpl implements TestReportService {

    private static final String STATUS_PASSED = "passed";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_BROKEN = "broken";

    private final TestCaseReportMapper caseReportMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 记录测试用例执行报告并生成Allure格式的测试结果
     * <p>此方法是Allure报告生成流程的核心起点，负责收集测试执行数据并构建Allure兼容的测试结果JSON</p>
     *
     * @param batchDTO 批次信息对象，包含执行批次相关数据
     * @param apiTest API测试注解，包含模块等元数据
     * @param useCase 测试用例对象，包含测试请求的详细信息
     * @param responseVO 响应结果对象，包含API响应数据
     * @param throwable 异常信息，如果测试过程中发生异常
     * @param startTime 测试开始时间
     * @param endTime 测试结束时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordCaseReport(BatchDTO batchDTO,
                                 ApiTest apiTest,
                                 UseCase useCase,
                                 ApiResponseVO responseVO,
                                 Throwable throwable,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime) {
        // 参数校验：确保必要的批次和用例信息存在
        if (batchDTO == null || useCase == null) {
            log.warn("批次参数或用例信息为空，跳过报告记录逻辑");
            return;
        }

        
        // 安全获取时间信息，避免空指针异常
        LocalDateTime safeStartTime = Optional.ofNullable(startTime).orElse(LocalDateTime.now());
        LocalDateTime safeEndTime = Optional.ofNullable(endTime).orElse(LocalDateTime.now());

        // 初始化测试报告相关字段
        String requestHeaders = defaultString(useCase.getHeader());
        String requestBody = defaultString(useCase.getParam());
        String responseHeaders = "";
        String responseBody = "";
        Integer responseStatus = null;
        Long duration = null;
        String assertDetail = "";
        String status;

        // 确定测试状态：如果有异常，则为broken状态
        if (throwable != null) {
            status = STATUS_BROKEN;
        } else if (responseVO != null) {
            // 从响应对象中提取测试结果信息
            responseStatus = responseVO.getStatus();
            duration = responseVO.getTime();

//            // 序列化响应头信息，用于报告存储
//            if (!CollectionUtils.isEmpty(responseVO.getHeaders())) {
//                try {
//                    responseHeaders = objectMapper.writeValueAsString(responseVO.getHeaders());
//                } catch (JsonProcessingException e) {
//                    log.warn("序列化响应头失败", e);
//                    responseHeaders = responseVO.getHeaders().toString();
//                }
//            }
            responseHeaders =JsonUtils.serializeForReport(responseVO.getHeaders());

//            // 序列化响应体信息，用于报告存储
//            if (!CollectionUtils.isEmpty(responseVO.getResult())) {
//                try {
//                    responseBody = objectMapper.writeValueAsString(responseVO.getResult());
//                } catch (JsonProcessingException e) {
//                    log.warn("序列化响应体失败", e);
//                    responseBody = responseVO.getResult().toString();
//                }
//            }
            requestBody= JsonUtils.serializeForReport(responseVO.getResult());

//            // 序列化断言结果，用于报告存储
//            if (!CollectionUtils.isEmpty(responseVO.getAssertResults())) {
//                try {
//                    assertDetail = objectMapper.writeValueAsString(responseVO.getAssertResults());
//                } catch (JsonProcessingException e) {
//                    log.warn("序列化断言结果失败", e);
//                    assertDetail = responseVO.getAssertResults().toString();
//                }
//            }

            assertDetail=JsonUtils.serializeForReport(responseVO.getAssertResults());


            // 根据响应结果评估测试状态（passed/failed）
            status = evaluateStatus(responseVO);
        } else {
            // 既无异常也无响应，表示测试中断
            status = STATUS_BROKEN;
        }

        // 构建Allure格式的测试结果JSON - 这是生成Allure报告的关键步骤
        String allureJson = buildAllureResultJson(apiTest, useCase, responseVO, throwable, safeStartTime, safeEndTime, status);

        // 构建测试报告实体对象，包含所有测试信息和Allure结果JSON
        TestCaseReport report = TestCaseReport.builder()
                .caseId(useCase.getId())
                .caseName(useCase.getName())
                .moduleName(apiTest != null ? apiTest.module() : null)
                .description(useCase.getDescription())
                .apiUrl(useCase.getUrl())
                .requestMethod(useCase.getMethod())
                .requestHeaders(requestHeaders)
                .requestBody(requestBody)
                .responseStatus(responseStatus)
                .responseHeaders(responseHeaders)
                .responseBody(responseBody)
                .duration(duration)
                .assertDetail(assertDetail)
                .status(status)
                .allureResultJson(allureJson) // 存储Allure格式的测试结果JSON
                .startTime(safeStartTime)
                .endTime(safeEndTime)
                .createdAt(LocalDateTime.now())
                .build();

        // 保存测试报告到数据库
        caseReportMapper.insert(report);
    }



@Override
public List<TestCaseReport> queryCaseReports(ReportQueryDTO queryDTO) {
    LambdaQueryWrapper<TestCaseReport> wrapper = new LambdaQueryWrapper<>();
    if (queryDTO != null) {
        if (StringUtils.hasText(queryDTO.getModuleName())) {
            wrapper.eq(TestCaseReport::getModuleName, queryDTO.getModuleName());
        }
        if (StringUtils.hasText(queryDTO.getCaseName())) {
            wrapper.like(TestCaseReport::getCaseName, queryDTO.getCaseName());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(TestCaseReport::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getStartTime() != null) {
            wrapper.ge(TestCaseReport::getStartTime, queryDTO.getStartTime());
        }
        if (queryDTO.getEndTime() != null) {
            wrapper.le(TestCaseReport::getEndTime, queryDTO.getEndTime());
        }
    }
    wrapper.orderByDesc(TestCaseReport::getStartTime);
    return caseReportMapper.selectList(wrapper);
}


public void writeAllureJson (List<TestCaseReport> reports,Path resultsDir)throws Exception {

        // 遍历每个测试报告，处理报告数据
        for (TestCaseReport report : reports) {
            // 解析Allure结果JSON字符串为Map对象
            String jsonStr=report.getAllureResultJson();
            Map<String, Object> allureResult = parseAllureJson(jsonStr);

            // 如果解析失败，跳过当前报告
            if (allureResult == null) {
                continue;
            }

            // 获取测试结果的UUID，如果没有则生成新的
            String uuid = Optional.ofNullable((String) allureResult.get("uuid"))
                    .orElse(UUID.randomUUID().toString());

            // 构建结果文件名，遵循Allure命名规范
            String resultFileName = uuid + "-result.json";

            Path jsonFile = resultsDir.resolve(resultFileName);
            Files.writeString(jsonFile,jsonStr,StandardCharsets.UTF_8);

            // 提取附件数据
            List<Map<String, Object>> attachments = extractAttachments(allureResult);
            List<Map.Entry<String, byte[]>> attachmentPayloads = new ArrayList<>();

            // 处理附件数据，将内容转换为字节数组并准备添加到ZIP文件
            if (!CollectionUtils.isEmpty(attachments)) {
                for (Map<String, Object> attachment : attachments) {
                    Object source = attachment.get("source");
                    Object content = attachment.remove("content");

                    // 确保附件源和内容都有效
                    if (source == null || content == null) {
                        continue;
                    }
                    // 直接写入附件，不需要存集合！
                    Path attachFile = resultsDir.resolve(source.toString());
                    Files.writeString(attachFile, content.toString(), StandardCharsets.UTF_8);
                }
            }


        }
}

/**
 * 导出Allure HTML报告压缩包
 * <p>
 * 该方法用于根据查询条件生成HTML格式的测试报告，并将其打包为ZIP文件返回
 * 实现步骤：
 * 1. 先生成Allure结果文件的ZIP压缩包
 * 2. 创建临时目录用于处理文件
 * 3. 解压结果文件到临时目录
 * 4. 根据结果文件生成HTML报告
 * 5. 将生成的HTML报告打包为ZIP并返回
 *
 * @param queryDTO 查询条件对象，包含批次ID、时间范围等过滤条件
 * @return 生成的HTML报告ZIP文件的字节数组，如果生成失败则返回空数组
 */
@Override
public byte[] exportAllureHtmlZip(ReportQueryDTO queryDTO) {


    List<TestCaseReport> reports = queryCaseReports(queryDTO);
    if (CollectionUtils.isEmpty(reports)) {
        return new byte[0];
    }

    // 2) 创建临时目录用于文件处理
    // 临时目录用于存储中间处理结果，避免影响其他文件
    Path tempRoot = null;
    try {
        // 创建临时根目录，前缀为"allure_build_"
        // 使用系统提供的临时目录机制，确保跨平台兼容性
        tempRoot = Files.createTempDirectory("allure_build_");

        // 创建存放解压结果的目录
        // 用于存储从resultsZip中解压出的Allure结果文件
        Path resultsDir = tempRoot.resolve("results");

        // 创建存放生成HTML报告的目录
        // 用于接收Allure生成器生成的HTML报告文件
        Path reportDir = tempRoot.resolve("report");

        // 确保目录存在
        Files.createDirectories(resultsDir);
        Files.createDirectories(reportDir);


        writeAllureJson(reports, resultsDir);
        // 4) 使用Allure Java生成HTML报告
        // 调用Allure的命令行工具，根据解压后的结果文件生成标准的HTML报告
        generateHtmlWithAllureJava(resultsDir, reportDir);

        // 5) 将生成的HTML报告目录打包为zip并返回字节数组
        // 压缩整个报告目录，方便前端下载和使用
        return zipDirectory(reportDir);
    } catch (Exception e) {
        // 记录生成报告过程中的错误
        // 详细记录异常信息，便于问题排查
        log.error("生成Allure HTML报告失败", e);
        // 出错时返回空数组
        // 采用防御性编程方式，确保即使出错也能返回有效的响应
        return new byte[0];
    } finally {
        // 清理临时文件，避免磁盘空间占用
        // 即使在异常情况下也能释放系统资源
        if (tempRoot != null) {
            try {
                deleteRecursively(tempRoot);
            } catch (IOException ignored) {
                // 忽略删除临时文件时的异常
                // 避免删除失败影响主流程
            }
        }
    }
}

    @Override
    public void deleteCaseReports(ReportQueryDTO queryDTO) {

        LambdaQueryWrapper<TestCaseReport> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO != null) {
            if (StringUtils.hasText(queryDTO.getModuleName())) {
                wrapper.eq(TestCaseReport::getModuleName, queryDTO.getModuleName());
            }
            if (StringUtils.hasText(queryDTO.getCaseName())) {
                wrapper.like(TestCaseReport::getCaseName, queryDTO.getCaseName());
            }
            if (StringUtils.hasText(queryDTO.getStatus())) {
                wrapper.eq(TestCaseReport::getStatus, queryDTO.getStatus());
            }
            if (queryDTO.getStartTime() != null) {
                wrapper.ge(TestCaseReport::getStartTime, queryDTO.getStartTime());
            }
            if (queryDTO.getEndTime() != null) {
                wrapper.le(TestCaseReport::getEndTime, queryDTO.getEndTime());
            }


            caseReportMapper.delete(wrapper);

        }





    }

    /**
 * 调用 Allure CLI 生成 HTML 报告
 * <p>
 * 基于已经准备好的 allure-results 目录，通过执行
 *   allure generate <resultsDir> -o <reportDir> --clean
 * 命令生成标准的 Allure HTML 报告。
 * </p>
 *
 * @param resultsDir 含有 *-result.json 及附件文件的目录
 * @param reportDir  Allure 生成 HTML 报告的输出目录
 * @throws Exception 当调用 Allure 失败或退出码非 0 时抛出异常
 */
private void generateHtmlWithAllureJava(Path resultsDir, Path reportDir) throws Exception {
    // 确保输出目录存在
    // 在生成报告前，先确保目标目录已创建，避免因目录不存在导致生成失败
    Files.createDirectories(reportDir);

    // 构建 Allure 命令
    // 使用ArrayList存储命令参数，方便后续维护和扩展
    List<String> command = new ArrayList<>();

    // 替换为 allure.bat 的绝对路径（注意Java中反斜杠需要转义，用双反斜杠\\）
    // 此处使用Windows系统下的allure.bat脚本路径，在Linux/Mac环境下需改为allure.sh
    String allurePath = "allure.bat";
    command.add(allurePath);
    // 指定操作类型为generate，表示生成HTML报告
    command.add("generate");

    // 指定Allure结果文件所在目录，使用绝对路径确保正确性
    command.add(resultsDir.toAbsolutePath().toString());

    // 指定输出目录参数
    command.add("-o");

    // 指定HTML报告输出目录，使用绝对路径确保正确性
    command.add(reportDir.toAbsolutePath().toString());

    // 指定--clean参数，表示在生成前清空输出目录
    // 避免多次生成报告时产生文件冲突或冗余文件
    command.add("--clean");

    // 记录开始生成报告的日志，包含完整命令，便于问题排查
    log.info("开始调用 Allure CLI 生成报告，命令: {}", String.join(" ", command));

    // 创建进程构建器，用于执行外部命令
    ProcessBuilder processBuilder = new ProcessBuilder(command);

    // 继承当前进程的环境变量（包括 ALLURE_HOME、PATH 等）
    // 确保Allure命令能够正确执行
    processBuilder.redirectErrorStream(true); // 合并 stdout 和 stderr
    // 将标准错误流合并到标准输出流，便于统一收集输出信息

    // 启动Allure命令进程
    Process process = processBuilder.start();

    // 收集输出日志，便于排查问题
    // 使用StringBuilder高效拼接大量文本
    StringBuilder output = new StringBuilder();

    // 使用try-with-resources自动关闭资源
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        // 逐行读取命令输出
        while ((line = reader.readLine()) != null) {
            output.append(line).append(System.lineSeparator());
        }
    }

    // 等待进程执行完毕，并获取退出码
    int exitCode = process.waitFor();

    // 检查退出码，如果不为0表示命令执行失败
    if (exitCode != 0) {
        // 记录错误日志，包含退出码和完整输出信息
        log.error("Allure CLI 生成报告失败，退出码: {}，输出信息: \n{}", exitCode, output);
        // 抛出异常，通知调用方生成失败
        throw new IllegalStateException("调用 Allure CLI 失败，退出码: " + exitCode);
    }

    // 记录成功日志，包含输出目录信息
    log.info("Allure CLI 生成报告成功，输出目录: {}", reportDir.toAbsolutePath());
}







/**
 * 将目录打包为ZIP字节数组
 * <p>
 * 该方法用于将生成的Allure HTML报告目录打包为ZIP格式，便于前端下载
 * 递归处理目录下的所有文件，保持相对路径结构
 * </p>
 *
 * @param directory 要打包的目录路径
 * @return 打包后的ZIP文件字节数组
 * @throws IOException 打包过程中出现的IO异常
 */
private byte[] zipDirectory(Path directory) throws IOException {
    // 使用try-with-resources自动关闭字节数组输出流和ZIP输出流
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
        // 递归遍历目录下的所有文件
        Files.walk(directory)
                // 仅处理普通文件，跳过目录
                .filter(Files::isRegularFile)
                // 对每个文件进行处理
                .forEach(path -> {
                    // 计算文件在ZIP中的相对路径
                    // 将Windows风格的反斜杠替换为标准的斜杠，确保跨平台兼容性
                    String entryName = directory.relativize(path).toString().replace("\\", "/");
                    try {
                        // 创建ZIP条目并写入ZIP输出流
                        zos.putNextEntry(new ZipEntry(entryName));
                        // 直接将文件内容复制到ZIP输出流
                        Files.copy(path, zos);
                        // 关闭当前ZIP条目
                        zos.closeEntry();
                    } catch (IOException e) {
                        // 将IO异常包装为运行时异常抛出
                        throw new RuntimeException(e);
                    }
                });
        // 完成ZIP文件的写入
        zos.finish();
        // 返回生成的ZIP字节数组
        return bos.toByteArray();
    }
}

/**
 * 递归删除目录及其所有内容
 * <p>
 * 该方法用于清理Allure报告生成过程中创建的临时目录
 * 采用倒序删除策略，确保先删除文件后删除目录，避免删除顺序问题
 * </p>
 *
 * @param path 要删除的目录路径
 * @throws IOException 删除过程中出现的IO异常
 */
private void deleteRecursively(Path path) throws IOException {
    // 如果路径不存在，直接返回
    if (Files.notExists(path)) {
        return;
    }
    // 递归遍历目录，获取所有文件和子目录
    Files.walk(path)
            // 按相反顺序排序，确保先删除文件和子目录，再删除父目录
            .sorted(Comparator.reverseOrder())
            // 对每个路径进行删除操作
            .forEach(p -> {
                try {
                    // 使用deleteIfExists避免文件不存在时抛出异常
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 忽略删除过程中的异常，继续删除其他文件
                }
            });
}

/**
 * 评估测试用例的执行状态
 * <p>
 * 该方法根据HTTP响应状态和断言结果来确定测试用例的最终状态
 * 遵循Allure报告的状态定义规范：passed（通过）、failed（失败）、broken（损坏）
 * </p>
 *
 * @param responseVO API响应对象，包含HTTP状态码和断言结果
 * @return 测试用例的状态，值为"passed"、"failed"或"broken"
 */
private String evaluateStatus(ApiResponseVO responseVO) {
    // 检查HTTP请求是否成功（状态码小于400）
    boolean httpSuccess = responseVO.getStatus() != null && responseVO.getStatus() < 400;

    // 检查断言是否全部成功
    boolean assertSuccess = CollectionUtils.isEmpty(responseVO.getAssertResults()) ||
            responseVO.getAssertResults().stream().allMatch(result -> Boolean.TRUE.equals(result.getResult()));

    // 如果HTTP请求成功且所有断言通过，则状态为passed
    if (httpSuccess && assertSuccess) {
        return STATUS_PASSED;
    }

    // 如果HTTP请求失败或断言未全部通过，则状态为failed
    if (!httpSuccess || !assertSuccess) {
        return STATUS_FAILED;
    }

    // 其他情况视为broken（一般不会执行到这里）
    return STATUS_BROKEN;
}

private String buildAllureResultJson(ApiTest apiTest, UseCase useCase, ApiResponseVO responseVO, Throwable throwable, LocalDateTime startTime, LocalDateTime endTime, String status) {
    // 创建有序的结果Map，保证JSON输出的字段顺序一致
    Map<String, Object> result = new LinkedHashMap<>();

    // 生成唯一标识符，用于区分不同的测试结果
    String uuid = UUID.randomUUID().toString();
    result.put("uuid", uuid);

    // 生成历史ID，用于Allure的趋势分析和历史记录功能
    // 使用用例名称和URL作为输入，确保相同用例的历史ID一致
    result.put("historyId", UUID.nameUUIDFromBytes((useCase.getName() + useCase.getUrl()).getBytes(StandardCharsets.UTF_8)).toString());


    // 设置测试用例名称
    result.put("name", useCase.getName());

    // 设置完整名称，格式为：模块名.用例名
    // 如果没有指定模块，则默认为空字符串
    result.put("fullName", Optional.ofNullable(apiTest).map(ApiTest::module).orElse("") + "." + useCase.getName());

    // 设置测试状态：passed/failed/broken
    result.put("status", status);

    // 设置测试阶段，固定为finished表示测试已完成
    result.put("stage", "finished");

    // 设置开始时间戳，转换为毫秒级时间戳
    result.put("start", toEpochMilli(startTime));

    // 设置结束时间戳，转换为毫秒级时间戳
    result.put("stop", toEpochMilli(endTime));

    // 如果存在异常，添加状态详情信息
    if (throwable != null) {
        // 创建异常详情Map
        Map<String, Object> detail = new HashMap<>();
        // 添加异常消息
        detail.put("message", throwable.getMessage());
        // 添加异常堆栈信息，便于问题排查
        detail.put("trace", stackTraceToString(throwable));
        // 将详情添加到结果中
        result.put("statusDetails", detail);
    }

    // 添加Allure标签，用于分类和筛选测试结果
    List<Map<String, String>> labels = new ArrayList<>();
    // 添加套件标签，表示所属模块
    labels.add(label("suite", Optional.ofNullable(apiTest).map(ApiTest::module).orElse("默认模块")));
    // 添加主机标签，标识测试执行的主机
    labels.add(label("host", "local"));
    // 添加线程标签，标识执行测试的线程
    labels.add(label("thread", Thread.currentThread().getName()));
    // 添加特性标签，使用用例描述作为特性名称
    labels.add(label("feature", defaultString(useCase.getDescription())));
    // 将标签列表添加到结果中
    result.put("labels", labels);

    // 添加测试参数，展示测试的关键信息
    List<Map<String, Object>> parameters = new ArrayList<>();
    // 添加接口地址参数
    parameters.add(parameter("接口地址", useCase.getUrl()));
    // 添加HTTP方法参数
    parameters.add(parameter("HTTP方法", useCase.getMethod()));
    // 添加请求头参数
    parameters.add(parameter("请求头", defaultString(useCase.getHeader())));
    // 添加请求体参数
    parameters.add(parameter("请求体", defaultString(useCase.getParam())));
    // 将参数列表添加到结果中
    result.put("parameters", parameters);

    // 添加附件，用于存储响应体和断言结果等额外信息
    List<Map<String, Object>> attachments = new ArrayList<>();
    if (responseVO != null) {
        // 添加响应体附件
        attachments.add(attachment("响应体", "application/json", uuid + "-response.json",
                safeToJson(responseVO.getResult())));
        // 添加断言结果附件
        attachments.add(attachment("断言结果", "application/json", uuid + "-assert.json",
                safeToJson(responseVO.getAssertResults())));
    }
    // 只有在附件列表非空时才添加到结果中
    if (!attachments.isEmpty()) {
        result.put("attachments", attachments);
    }

    // 将结果Map转换为JSON字符串
    try {
        return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
        // 序列化失败时记录错误日志
        log.error("序列化Allure结果失败", e);
        // 返回空JSON对象作为兜底
        return "{}";
    }
}

/**
 * 解析Allure结果JSON字符串
 * <p>
 * 将Allure结果JSON字符串转换为Map对象，以便提取报告中的各项信息
 * 处理流程：
 * 1. 首先检查JSON字符串是否有效
 * 2. 使用Jackson库将JSON反序列化为Map对象
 * 3. 处理可能出现的序列化异常
 * </p>
 *
 * @param json Allure结果的JSON字符串
 * @return 解析后的Map对象，包含测试结果的各项信息；如果解析失败返回null
 */
/**
 * 解析Allure结果JSON字符串
 * <p>
 * 该方法负责将存储在数据库中的Allure报告JSON字符串解析为Java对象
 * 用于后续的报告处理、附件提取和ZIP打包操作
 * 实现了完整的输入验证和异常处理
 * </p>
 *
 * @param json Allure结果的JSON字符串
 * @return 解析后的Map对象，包含测试结果的各项信息；如果解析失败返回null
 */
private Map<String, Object> parseAllureJson(String json) {
    // 首先验证输入的JSON字符串是否有效
    // StringUtils.hasText方法会检查字符串是否为null、空字符串或仅包含空白字符
    if (!StringUtils.hasText(json)) {
        // 无效输入时返回null，避免后续处理空字符串导致异常
        return null;
    }
    try {
        // 使用Jackson的ObjectMapper将JSON字符串反序列化为Map对象
        // 通过TypeReference指定目标类型为Map<String, Object>，确保类型安全
        // 这种方式可以保留JSON中的所有数据类型和结构
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    } catch (JsonProcessingException e) {
        // 捕获JSON解析过程中可能出现的异常
        // 记录警告日志，包含异常详情，便于问题排查
        log.warn("解析Allure JSON失败", e);
        // 解析失败时返回null，调用方需要处理null返回值情况
        return null;
    }
}

/**
 * 从Allure结果对象中提取附件列表
 * <p>
 * 该方法从Allure报告结果中解析出所有附件信息，用于后续处理和展示
 * 实现了类型安全检查和异常处理，确保即使数据格式异常也不会影响程序运行
 * </p>
 *
 * @param allureResult Allure报告结果对象
 * @return 附件列表，如果没有附件或解析失败则返回空列表
 */
@SuppressWarnings("unchecked")
private List<Map<String, Object>> extractAttachments(Map<String, Object> allureResult) {
    // 从Allure结果对象中获取attachments字段的值
    Object attachmentsObj = allureResult.get("attachments");

    // 检查获取到的值是否为List类型，如果不是则返回空列表
    if (!(attachmentsObj instanceof List)) {
        return Collections.emptyList();
    }

    // 将attachmentsObj转换为List<?>类型，以便后续遍历
    List<?> attachmentList = (List<?>) attachmentsObj;

    // 创建一个用于存储类型转换后附件对象的列表
    List<Map<String, Object>> attachments = new ArrayList<>();

    // 遍历所有附件对象
    for (Object obj : attachmentList) {
        // 检查每个对象是否为Map类型，如果是则添加到结果列表
        if (obj instanceof Map) {
            attachments.add((Map<String, Object>) obj);
        }
    }

    // 返回提取出的附件对象列表
    return attachments;
}

/**
 * 创建Allure报告标签对象
 * <p>
 * 标签用于对测试用例进行分类、过滤和组织
 * 常见的标签类型包括suite（套件）、feature（特性）、host（主机）、thread（线程）等
 * </p>
 *
 * @param name 标签名称，如"suite"、"feature"、"host"等
 * @param value 标签值，如模块名称、功能描述等
 * @return 包含标签名称和值的Map对象
 */
private Map<String, String> label(String name, String value) {
    Map<String, String> map = new HashMap<>(2);
    map.put("name", name);
    map.put("value", value);
    return map;
}

/**
 * 创建Allure报告参数对象
 * <p>
 * 参数用于展示测试用例的关键信息，如接口地址、请求方法、请求头等
 * 参数会显示在测试报告的详细信息部分
 * </p>
 *
 * @param name 参数名称，如"接口地址"、"HTTP方法"等
 * @param value 参数值，可以是任意类型的对象
 * @return 包含参数名称和值的Map对象
 */
private Map<String, Object> parameter(String name, Object value) {
    Map<String, Object> map = new HashMap<>(2);
    map.put("name", name);
    map.put("value", value);
    return map;
}

/**
 * 创建Allure报告附件对象
 * <p>
 * 附件用于存储测试过程中的额外信息，如响应体、断言结果、截图等
 * 附件会以独立文件形式存储在Allure报告中，并在UI中可点击查看
 * </p>
 *
 * @param name 附件名称，如"响应体"、"断言结果"等
 * @param type 附件MIME类型，如"application/json"、"image/png"等
 * @param source 附件源文件名，用于在报告中标识附件文件
 * @param content 附件内容，通常为JSON字符串、图片数据等
 * @return 包含附件所有必要信息的Map对象
 */
private Map<String, Object> attachment(String name, String type, String source, String content) {
    Map<String, Object> map = new HashMap<>(4);
    map.put("name", name);
    map.put("type", type);
    map.put("source", source);
    map.put("content", content);
    return map;
}

/**
 * 安全地将对象转换为JSON字符串
 * <p>
 * 封装了Jackson的JSON序列化功能，并处理了可能发生的异常
 * 确保即使序列化失败也不会导致程序崩溃
 * </p>
 *
 * @param data 要序列化的对象，可以是任意类型
 * @return JSON字符串，序列化失败或对象为null时返回空字符串
 */
private String safeToJson(Object data) {
    if (data == null) {
        return "";
    }
    try {
        return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException e) {
        return data.toString();
    }
}

/**
 * 将异常对象转换为字符串形式的堆栈信息
 * <p>
 * 提取异常的完整堆栈跟踪信息，用于在报告中展示错误详情
 * 便于问题定位和调试
 * </p>
 *
 * @param throwable 异常对象
 * @return 格式化的堆栈跟踪字符串
 */
private String stackTraceToString(Throwable throwable) {
    return Arrays.stream(throwable.getStackTrace())
            .map(StackTraceElement::toString)
            .collect(Collectors.joining(System.lineSeparator()));
}

/**
 * 将LocalDateTime对象转换为毫秒级时间戳
 * <p>
 * Allure报告使用时间戳表示测试的开始和结束时间
 * 本方法提供了从Java 8日期时间API到时间戳的转换
 * </p>
 *
 * @param time LocalDateTime对象
 * @return 毫秒级时间戳
 */
private long toEpochMilli(LocalDateTime time) {
    return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
}

/**
 * 处理字符串的默认值
 * <p>
 * 当字符串为null或空白时返回空字符串，否则返回原字符串
 * 用于确保Allure报告中的字段不会显示为null
 * </p>
 *
 * @param str 原始字符串
 * @return 处理后的字符串，空白字符串会被转换为空字符串
 */
private String defaultString(String str) {
    return StringUtils.hasText(str) ? str : "";
}
}

