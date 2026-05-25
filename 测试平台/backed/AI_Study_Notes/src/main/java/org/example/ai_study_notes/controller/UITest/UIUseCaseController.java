package org.example.ai_study_notes.controller.UITest;


import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiUseCaseDTO;
import org.example.ai_study_notes.service.UIUseCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ui-use-cases")
public class UIUseCaseController {

    @Autowired
    private UIUseCaseService uiUseCaseService;

    /**
     * 根据项目ID获取 UI 用例列表
     */
    @GetMapping
    public List<UiUseCaseDTO> list(@RequestParam("pid") String projectId) {
        return uiUseCaseService.getUseCasesByProjectId(projectId);
    }

    /**
     * 新增 UI 用例
     */
    @PostMapping
    public Result add(@RequestBody UiUseCaseDTO dto) {
        uiUseCaseService.addUseCase(dto);
        return Result.success();
    }

    /**
     * 更新 UI 用例
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Integer id, @RequestBody UiUseCaseDTO dto) {
        dto.setId(id);
        uiUseCaseService.updateUseCase(dto);
        return Result.success();
    }

    /**
     * 删除 UI 用例
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") Integer id) {
        uiUseCaseService.deleteUseCase(id);
        return Result.success();
    }




    
}
