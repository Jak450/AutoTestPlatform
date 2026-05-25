package org.example.ai_study_notes.controller.UITest;


import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
import org.example.ai_study_notes.Pojo.entity.UI.UIProject;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.service.ProjectService;
import org.example.ai_study_notes.service.UIProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ui-projects")
public class UIProjectController {

    @Autowired
    private UIProjectService projectService;





 @GetMapping
 public List<UIProject> getProject() {


    List<UIProject> projects= projectService.getProject();

     return projects;
 }


 @PostMapping
    public Result addProject(@RequestBody UIProject projectDTO) {

        projectService.addProject(projectDTO);
     return Result.success();
 }

 @PutMapping("/{id}")
    public Result updateProject(@PathVariable int id, @RequestBody UIProject projectDTO) {

     projectService.updateProject(projectDTO);
     return Result.success();
 }

 @DeleteMapping("/{id}")
    public Result deleteProject(@PathVariable Integer id) {


     projectService.deleteProject(id);
    return Result.success();
 }

}
