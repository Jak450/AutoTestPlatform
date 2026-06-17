package org.example.ai_study_notes.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class RedisUtils {

  public RedisUtils() {}


    // 自定义日期格式（和数据库一致：yyyy-MM-dd HH:mm:ss，避免默认的 T 分隔符）
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory factory)
    {
       RedisTemplate<String,Object> template = new RedisTemplate<>();
       template.setConnectionFactory(factory);
       Jackson2JsonRedisSerializer<Object> Serializer = new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        Serializer.setObjectMapper(objectMapper);


        // 关键修改 1：注册 JavaTimeModule，支持 LocalDateTime/LocalDate 等 Java 8 日期类型
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 关键修改 2：自定义 LocalDateTime 序列化格式（转成和数据库一致的字符串）
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME_FORMATTER));
        objectMapper.registerModule(javaTimeModule);

        // 关键修改 3：禁用日期转时间戳（默认会把 LocalDateTime 转成 long，禁用后用字符串格式存储）
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);



        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(Serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(Serializer);
        template.afterPropertiesSet();
        return template;
    }

}
