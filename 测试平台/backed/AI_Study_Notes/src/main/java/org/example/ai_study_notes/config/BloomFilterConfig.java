//package org.example.ai_study_notes.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.stereotype.Component;
//import org.redisson.api.RBloomFilter;
//import org.redisson.api.RedissonClient;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.boot.CommandLineRunner;
//
//
////暂未使用
//
//@Configuration
//public class BloomFilterConfig {
//
//    /**
//     * 1. 定义 Bean，配置布隆过滤器的参数
//     */
//    @Bean
//    public RBloomFilter<Integer> useCaseBloomFilter(RedissonClient redissonClient) {
//        RBloomFilter<Integer> bloomFilter = redissonClient.getBloomFilter("bf:usecase:pid");
//
//        // 初始化布隆过滤器
//        // expectedInsertions: 预估数据量 (假设你有 10万个 pid，建议设为 20万)
//        // falseProbability: 误判率 (通常 0.01 或 0.03)
//        bloomFilter.tryInit(200000L, 0.01);
//
//        return bloomFilter;
//    }
//
//    /**
//     * 2. 数据预热 (应用启动时执行)
//     * 注意：生产环境数据量大时，建议用专门的定时任务或 XXL-JOB 跑，不要阻塞启动
//     */
////    @Bean
////    public CommandLineRunner warmUpBloomFilter(RBloomFilter<Integer> bloomFilter, UseCaseMapper useCaseMapper) {
////        return args -> {
////            // 只有当过滤器为空（可能是新部署）时才加载，避免重启重复加载
////            // 注意：count() 开销较大，实际生产中可以用 redis key 是否存在来判断是否需要预热
////            if (bloomFilter.count() == 0) {
////                System.out.println("正在预热布隆过滤器...");
////
////                // 查出所有的 PID (只需要 ID 字段)
////                // select distinct pid from use_case
////                List<Integer> allPids = useCaseMapper.selectAllPids();
////
////                for (Integer pid : allPids) {
////                    bloomFilter.add(pid);
////                }
////                System.out.println("布隆过滤器预热完成，共加载: " + allPids.size());
////            }
////        };
////    }
//}