package org.example.ai_study_notes.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
import org.example.ai_study_notes.Pojo.entity.Project;
import org.example.ai_study_notes.Pojo.entity.UI.UIProject;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.mapper.ProjectMapper;
import org.example.ai_study_notes.mapper.UIProjectMapper;
import org.example.ai_study_notes.service.ProjectService;
import org.example.ai_study_notes.service.UIProjectService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UIProjectServiceImpl implements UIProjectService {

    @Autowired
    private UIProjectMapper projectMapper;

    @Autowired
    private RedisTemplate redisTemplate;


    @Value("${example.ai_study_notes.redis.ttl}")
    private long redisTtl;

    private String projects="UIprojects";

    @Override
    public List<UIProject> getProject() {

        List<UIProject> ppp=(List<UIProject>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            return ppp;
        }

        QueryWrapper<UIProject> queryWrapper = new QueryWrapper<>();
        List<UIProject> projectList=projectMapper.selectList(queryWrapper);


        redisTemplate.opsForValue().set(projects,projectList,redisTtl, TimeUnit.SECONDS);
        return projectList;
    }

    @Override
    public void addProject(UIProject project) {
        List<UIProject> ppp=(List<UIProject>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            redisTemplate.delete(projects);
        }
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
    projectMapper.insert(project);
    }

    @Override
    public void updateProject(UIProject project) {

        List<UIProject> ppp=(List<UIProject>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            redisTemplate.delete(projects);
        }
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    @Override
    public void deleteProject(Integer id) {

        List<UIProject> ppp=(List<UIProject>)redisTemplate.opsForValue().get(projects);

        if(ppp!=null&&ppp.size()>0){

            redisTemplate.delete(projects);
        }

        projectMapper.deleteById(id);
    }
}
