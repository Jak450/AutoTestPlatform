package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Aop.anno.ApiTest;
import org.example.ai_study_notes.Pojo.dto.BatchDTO;
import org.example.ai_study_notes.Pojo.dto.ReportQueryDTO;
import org.example.ai_study_notes.Pojo.entity.TestCaseReport;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试报告服务
 */
public interface TestReportService {

    /**
     * 记录单条测试用例的执行结果，并自动维护批次信息
     *
     * @param batchDTO    前端上传的批次参数
     * @param apiTest     注解中的元数据
     * @param useCase     用例基本信息
     * @param responseVO  接口执行结果
     * @param throwable   执行过程抛出的异常，为空视为正常结束
     * @param startTime   用例开始执行时间
     * @param endTime     用例结束执行时间
     */
    void recordCaseReport(BatchDTO batchDTO,
                          ApiTest apiTest,
                          UseCase useCase,
                          ApiResponseVO responseVO,
                          Throwable throwable,
                          LocalDateTime startTime,
                          LocalDateTime endTime);

    /**
     * 根据条件查询测试用例报告列表
     *
     * @param queryDTO 查询条件
     * @return 用例报告列表
     */
    List<TestCaseReport> queryCaseReports(ReportQueryDTO queryDTO);

    /**
     * 根据条件查询批次列表
     *
     * @param queryDTO 查询条件
     * @return 批次列表
     */
    // 删除批次查询，改为仅按时间筛选用例



    /**
     * 生成并返回可直接打开的Allure HTML报告压缩包
     * <p>
     * 此方法是Allure报告生成的主入口，按照以下步骤生成完整的HTML测试报告：
     * 1. 根据查询条件获取符合要求的测试用例报告数据
     * 2. 将测试报告转换为Allure格式的结果文件（*-result.json）
     * 3. 调用Allure命令行工具生成标准HTML报告
     * 4. 将生成的HTML报告打包为ZIP文件返回
     * </p>
     *
     * @param queryDTO 查询条件，包含模块名、执行状态、时间范围等筛选条件
     * @return HTML报告zip字节数组，可直接解压并在浏览器中打开index.html查看报告
     */
    byte[] exportAllureHtmlZip(ReportQueryDTO queryDTO);

    void deleteCaseReports(ReportQueryDTO queryDTO);

}

