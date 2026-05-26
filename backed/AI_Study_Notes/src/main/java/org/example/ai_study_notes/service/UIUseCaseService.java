package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Pojo.dto.UiDTO.UiUseCaseDTO;

import java.util.List;

public interface UIUseCaseService {

    /**
     * 根据项目ID获取 UI 用例列表
     */
    List<UiUseCaseDTO> getUseCasesByProjectId(String projectId);

    /**
     * 新增 UI 用例
     */
    void addUseCase(UiUseCaseDTO dto);

    /**
     * 更新 UI 用例
     */
    void updateUseCase(UiUseCaseDTO dto);

    /**
     * 删除 UI 用例
     */
    void deleteUseCase(Integer id);
}


