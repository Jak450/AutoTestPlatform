package org.example.ai_study_notes.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.ai_study_notes.Pojo.entity.Project;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectMapper extends BaseMapper<Project> {

}
