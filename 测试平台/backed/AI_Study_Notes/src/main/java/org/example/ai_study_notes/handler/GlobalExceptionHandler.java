package org.example.ai_study_notes.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    /*
     * 处理所有Exception类型的异常（全局兜底）
     * 注意：参数类型必须是Exception，与@ExceptionHandler声明的类型一致
     */
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception e) { // 这里将ArithmeticException改为Exception

        /*
          打印报错信息日志
         */
        log.error("全局捕获异常：",e);
        String message;
        if (e instanceof NullPointerException) {
            message = "空指针错误：请检查对象是否为null";
        } else if (e instanceof IllegalArgumentException) {
            message = "参数错误：" + e.getMessage();
        } else {
            message = "服务器异常：" + e.getMessage();
        }
        return Result.error(message);
    }


}
