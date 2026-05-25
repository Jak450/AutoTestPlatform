package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Pojo.dto.UseCaseUpdateDTO;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.UseCaseVO;

import java.util.List;

public interface UseCaseService {
    List<UseCaseVO> getUseCases(Integer pid);

    void updateUseCase(UseCase useCase);

    UseCase getUseCasesById(Integer id);

    void addUseCase(UseCaseUpdateDTO useCaseUpdateDTO);

    void deleteUseCase(Integer id);
}
