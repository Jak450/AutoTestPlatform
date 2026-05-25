package org.example.ai_study_notes.controller.UITest;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestCaseRequestDTO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiTestResultVO;
import org.example.ai_study_notes.service.UiTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Validated
@RequestMapping("/api/ui-test")
public class UiTestController {

    @Autowired
    private UiTestService uiTestService;

    @PostMapping("/run")
    public Result<UiTestResultVO> run( @RequestBody UiTestCaseRequestDTO requestDTO) {
        UiTestResultVO uiTestResultVO=uiTestService.run(requestDTO);
        if (uiTestResultVO.isSuccess())
        {return Result.success(uiTestResultVO);}

        return Result.error(uiTestResultVO);
    }
}

