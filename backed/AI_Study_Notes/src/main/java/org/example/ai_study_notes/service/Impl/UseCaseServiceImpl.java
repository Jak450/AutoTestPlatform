package org.example.ai_study_notes.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.ai_study_notes.Pojo.dto.UseCaseUpdateDTO;
import org.example.ai_study_notes.Pojo.entity.Project;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.Pojo.vo.UseCaseVO;
import org.example.ai_study_notes.mapper.UseCaseMapper;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UseCaseServiceImpl implements UseCaseService {

    @Autowired
    private UseCaseMapper useCaseMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${example.ai_study_notes.redis.ttl}")
    private long redisTtl;


    @Override
    public List<UseCaseVO> getUseCases(Integer pid) {

        String usercases="usercases"+":"+pid;
        List<UseCaseVO> ppp=(List<UseCaseVO>)redisTemplate.opsForValue().get(usercases);

        if(ppp!=null&&ppp.size()>0){

            return ppp;
        }

        QueryWrapper<UseCase> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pid", pid);

     List<UseCase> useCaseList=useCaseMapper.selectList(queryWrapper);
     List<UseCaseVO> useCaseVOList=new ArrayList<>();
        for (UseCase useCase : useCaseList) {
            UseCaseVO useCaseVO=new UseCaseVO();
            BeanUtils.copyProperties(useCase,useCaseVO);
            useCaseVOList.add(useCaseVO);
        }

        redisTemplate.opsForValue().set(usercases,useCaseVOList,redisTtl, TimeUnit.SECONDS);
        return useCaseVOList;
    }

    @Override
    public void updateUseCase(UseCase useCase) {
        Integer id=useCase.getId();
        String usecase="usercase"+":"+id;
        String usercases1="usercases"+":"+getUseCasesById(id).getPid();
        String usercases2="usercases"+":"+useCase.getPid();
        UseCase ppp=(UseCase)redisTemplate.opsForValue().get(usecase);
        if(ppp!=null){
            redisTemplate.delete(usecase);
            redisTemplate.delete(usercases1);
            redisTemplate.delete(usercases2);
        }

        useCaseMapper.insertOrUpdate(useCase);

    }

    @Override
    public UseCase getUseCasesById(Integer id) {


        String usecase="usercase"+":"+id;

        UseCase ppp=(UseCase)redisTemplate.opsForValue().get(usecase);
        if(ppp!=null){
            return ppp;
        }

        UseCase useCase=useCaseMapper.selectById(id);

        redisTemplate.opsForValue().set(usecase,useCase,redisTtl, TimeUnit.SECONDS);

        return useCase;
    }

    @Override
    public void addUseCase(UseCaseUpdateDTO useCaseUpdateDTO) {
        UseCase useCase=new UseCase();
        BeanUtils.copyProperties(useCaseUpdateDTO,useCase);
        useCase.setDescription(useCaseUpdateDTO.getDesc());
        useCaseMapper.insert(useCase);

    }

    @Override
    public void deleteUseCase(Integer id) {

        String usecase="usercase"+":"+id;
        String usercases="usercases"+":"+getUseCasesById(id).getPid();

        UseCase ppp=(UseCase)redisTemplate.opsForValue().get(usecase);
        if(ppp!=null){
            redisTemplate.delete(usecase);
            redisTemplate.delete(usercases);
        }

        useCaseMapper.deleteById(id);
    }


}
