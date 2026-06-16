package org.example.ai_study_notes.controller.UITest;


import org.example.ai_study_notes.Aop.anno.Idempotent;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiBatchExecuteResultVO;
import org.example.ai_study_notes.service.UiTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UIBatchTestController {

@Autowired
private UiTestService uiTestService;

    @PostMapping("ui-batch-test")
    @Idempotent
    public UiBatchExecuteResultVO BatchTest(@RequestBody BatchExecuteDTO batchExecuteDTO)
    {
        return uiTestService.batchTest(batchExecuteDTO);
    }


}
