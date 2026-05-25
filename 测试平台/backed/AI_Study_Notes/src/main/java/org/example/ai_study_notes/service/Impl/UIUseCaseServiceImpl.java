package org.example.ai_study_notes.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestStepDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiUseCaseDTO;
import org.example.ai_study_notes.Pojo.entity.UI.UIUseCase;
import org.example.ai_study_notes.mapper.UIUseCaseMapper;
import org.example.ai_study_notes.service.UIUseCaseService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UIUseCaseServiceImpl implements UIUseCaseService {

    private final UIUseCaseMapper uiUseCaseMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<UiUseCaseDTO> getUseCasesByProjectId(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return Collections.emptyList();
        }
        QueryWrapper<UIUseCase> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        List<UIUseCase> list = uiUseCaseMapper.selectList(wrapper);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public void addUseCase(UiUseCaseDTO dto) {
        if (dto == null) {
            return;
        }
        UIUseCase entity = new UIUseCase();
        BeanUtils.copyProperties(dto, entity);
        entity.setSteps(serializeSteps(dto.getSteps()));
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        uiUseCaseMapper.insert(entity);
    }

    @Override
    public void updateUseCase(UiUseCaseDTO dto) {
        if (dto == null || dto.getId()==null) {
            return;
        }
        UIUseCase entity = new UIUseCase();
        BeanUtils.copyProperties(dto, entity);
        entity.setSteps(serializeSteps(dto.getSteps()));
        entity.setUpdateTime(LocalDateTime.now());
        uiUseCaseMapper.updateById(entity);
    }

    @Override
    public void deleteUseCase(Integer id) {
        if (id==null) {
            return;
        }
        uiUseCaseMapper.deleteById(id);
    }

    /**
     * Entity -> DTO
     */
    private UiUseCaseDTO toDto(UIUseCase entity) {
        UiUseCaseDTO dto = new UiUseCaseDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setSteps(deserializeSteps(entity.getSteps()));
        return dto;
    }

    /**
     * 序列化步骤列表
     */
    private String serializeSteps(List<UiTestStepDTO> steps) {
        if (CollectionUtils.isEmpty(steps)) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(steps);
        } catch (Exception e) {
            log.error("序列化 UI 步骤失败: {}", steps, e);
            return "[]";
        }
    }

    /**
     * 反序列化步骤列表
     */
    private List<UiTestStepDTO> deserializeSteps(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<UiTestStepDTO>>() {
            });
        } catch (IOException e) {
            log.error("反序列化 UI 步骤失败: {}", json, e);
            return Collections.emptyList();
        }
    }
}


