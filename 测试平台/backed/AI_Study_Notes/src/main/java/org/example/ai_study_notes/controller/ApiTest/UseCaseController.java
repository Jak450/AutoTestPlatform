package org.example.ai_study_notes.controller.ApiTest;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.UseCaseUpdateDTO;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.UseCaseVO;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/use-cases")
public class UseCaseController {

    @Autowired
    private UseCaseService useCaseService;

    @GetMapping()
    public List<UseCaseVO> getUseCases(Integer pid) {

        List<UseCaseVO> list =useCaseService.getUseCases(pid);

        return list;
    }


    @PutMapping("/{id}")
    public Result updateUseCase(@PathVariable Integer id, @RequestBody UseCase useCase) {


       useCaseService.updateUseCase(useCase);
        return Result.success();
    }

    @GetMapping("/{id}")
    public UseCase getUseCaseById(@PathVariable Integer id) {


        UseCase useCaseUpdateVO=useCaseService.getUseCasesById(id);


        return useCaseUpdateVO;
    }

    @PostMapping
    public Result addUseCase(@RequestBody UseCaseUpdateDTO useCaseUpdateDTO) {

        useCaseService.addUseCase(useCaseUpdateDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result deleteUseCase(@PathVariable Integer id)
    {


        useCaseService.deleteUseCase(id);
        return Result.success("删除成功!");
    }

}
