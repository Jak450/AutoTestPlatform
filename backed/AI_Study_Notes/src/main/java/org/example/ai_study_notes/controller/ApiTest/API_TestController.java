package org.example.ai_study_notes.controller.ApiTest;

import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.service.ApiTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class API_TestController {
    @Autowired
    ApiTestService apiTestService;


    @PostMapping
    public ApiResponseVO ApiTest(@RequestBody ApiRequestDTO apiRequestDTO)
    {

        ApiResponseVO apiResponseVO =apiTestService.run(apiRequestDTO);;
        return apiResponseVO;
    }



}
