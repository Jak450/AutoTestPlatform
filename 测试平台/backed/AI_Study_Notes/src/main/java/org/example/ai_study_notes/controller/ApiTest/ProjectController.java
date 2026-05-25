package org.example.ai_study_notes.controller.ApiTest;


import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;





 @GetMapping
 public List<ProjectVO> getProject() {


    List<ProjectVO> projectVO= projectService.getProject();

     return projectVO;
 }


 @PostMapping
    public Result addProject(@RequestBody ProjectDTO projectDTO) {

        projectService.addProject(projectDTO);
     return Result.success();
 }

 @PutMapping("/{id}")
    public Result updateProject(@PathVariable int id, @RequestBody ProjectDTO projectDTO) {

     projectService.updateProject(projectDTO);
     return Result.success();
 }

 @DeleteMapping("/{id}")
    public Result deleteProject(@PathVariable Integer id) {


     projectService.deleteProject(id);
    return Result.success();
 }

}
