package org.example.ai_study_notes.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
import org.example.ai_study_notes.Pojo.entity.Project;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.mapper.ProjectMapper;
import org.example.ai_study_notes.service.ProjectService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private RedisTemplate redisTemplate;


    @Value("${example.ai_study_notes.redis.ttl}")
    private long redisTtl;

    private String projects="projects";

    @Override
    public List<ProjectVO> getProject() {

        List<ProjectVO> ppp=(List<ProjectVO>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            return ppp;
        }

        QueryWrapper<Project> queryWrapper = new QueryWrapper<>();
        List<Project> projectList=projectMapper.selectList(queryWrapper);
        List<ProjectVO> projectVOList=new ArrayList<>();
        for (Project project : projectList) {
            ProjectVO projectVO = new ProjectVO();
            BeanUtils.copyProperties(project, projectVO);
            projectVOList.add(projectVO);
        }

        redisTemplate.opsForValue().set(projects,projectVOList,redisTtl, TimeUnit.SECONDS);
        return projectVOList;
    }

    @Override
    public void addProject(ProjectDTO projectDTO) {

        List<ProjectVO> ppp=(List<ProjectVO>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            redisTemplate.delete(projects);
        }

    Project project=new Project();
    BeanUtils.copyProperties(projectDTO, project);
    projectMapper.insert(project);


    }

    @Override
    public void updateProject(ProjectDTO projectDTO) {

        List<ProjectVO> ppp=(List<ProjectVO>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            redisTemplate.delete(projects);
        }

        Project project=new Project();
        BeanUtils.copyProperties(projectDTO, project);
        projectMapper.updateById(project);
    }

    @Override
    public void deleteProject(Integer id) {

        List<ProjectVO> ppp=(List<ProjectVO>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            redisTemplate.delete(projects);
        }

        projectMapper.deleteById(id);
    }
}
