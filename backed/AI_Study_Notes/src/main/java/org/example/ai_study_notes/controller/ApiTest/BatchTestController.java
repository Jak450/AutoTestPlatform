package org.example.ai_study_notes.controller.ApiTest;

import org.example.ai_study_notes.Aop.anno.ApiTest;
import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.dto.BatchDTO;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;
import org.example.ai_study_notes.service.ApiTestService;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BatchTestController {


    @Autowired
    private ApiTestService apiTestService;


    @PostMapping("/execute")
    @ApiTest(name = "接口测试",module = "批量执行",description = "并发执行多个接口测试用例")
    public BatchExecuteResultVO batchExecute(@RequestBody BatchExecuteDTO batchExecuteDTO) {
        return apiTestService.batchExecute(batchExecuteDTO);
    }
    

}


//不使用下列代码
//    @PostMapping("/uploadData")
//    public List<ApiResponseVO> UploadData(@RequestBody FileDataDTO uploadDataDTO)
//    {
//        List<ApiResponseVO> api =new ArrayList<>();
//        List<ApiRequestDTO> apiRequestDTOList =uploadDataDTO.getFileData();
//
//        for (ApiRequestDTO apiRequestDTO : apiRequestDTOList) {
//            ApiResponseVO apiResponseVO =apiTestService.run(apiRequestDTO);
//            api.add(apiResponseVO);
//        }
//
//        return api;
//    }