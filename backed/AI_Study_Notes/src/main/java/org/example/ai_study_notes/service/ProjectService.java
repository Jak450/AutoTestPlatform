package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
import org.example.ai_study_notes.Pojo.entity.Project;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.springframework.stereotype.Service;

import java.util.List;


public interface ProjectService {

    public List<ProjectVO> getProject();

    void addProject(ProjectDTO projectDTO);

    void updateProject(ProjectDTO projectDTO);

    void deleteProject(Integer ID);
}
