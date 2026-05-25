package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
import org.example.ai_study_notes.Pojo.entity.UI.UIProject;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;

import java.util.List;


public interface UIProjectService {

    public List<UIProject> getProject();

    void addProject(UIProject projectDTO);

    void updateProject(UIProject projectDTO);

    void deleteProject(Integer ID);
}
