package com.example.blog.service.limit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.example.blog.enums.StatusLimit;
import com.example.blog.interfaces.RedisLimit;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;


@Service
public class RedisLimitService implements RedisLimit {
    private  final RedisClient redisClient;
    private  static final String SLIDE_SCRIPT=loadScript("lua/slide.lua");
    private RedisLimitService(@Qualifier ("asyncClient") RedisClient redisClient){
        this.redisClient=redisClient;
    }

    private static String loadScript(String classpath){
        try (var in=new  ClassPathResource(classpath).getInputStream()){
           return new String(in.readAllBytes(),StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new  UncheckedIOException("加载lua脚本失败："+ classpath,e);
        }
    }
    public <K, V> StatusLimit expiredLimit(
            int second,int timeOut , K key, V value, RedisCodec<K, V> codec) {
        try (StatefulRedisConnection<K, V> connection = redisClient.connect(codec)) {
            RedisAsyncCommands<K, V> async=connection.async();
            try {
                String result=async.set(key, value,SetArgs.Builder.ex(second).nx()).get(timeOut,TimeUnit.SECONDS);
                return "OK".equals(result) ? StatusLimit.SUCCESS : StatusLimit.FAULT;
            } catch (TimeoutException e) {
                // *秒内没等到 Redis 响应
                return StatusLimit.TIMEOUT;
            } catch (InterruptedException e) {
                // 等待期间线程被中断:恢复中断标记后返回
                Thread.currentThread().interrupt();
                return StatusLimit.CANCEL;
            } catch (ExecutionException e) {
                // Redis 侧命令执行失败
                return StatusLimit.FAULT;
            }
        }
    }
    @Override
    public  <K> StatusLimit fix(int max,int second,int timeOut,K key,RedisCodec<K,Integer> codec){
          try (StatefulRedisConnection<K, Integer> connection = redisClient.connect(codec)) {
            RedisAsyncCommands<K,Integer> async=connection.async();
            try{Long count=async.incr(key).get(timeOut,TimeUnit.MILLISECONDS);
                if (count==1) {
                    async.expire(key,second);
                }
            }catch (TimeoutException e) {
                // * 毫米内没等到 Redis 响应
                return StatusLimit.TIMEOUT;
            }  catch (InterruptedException e) {
                // 等待期间线程被中断:恢复中断标记后返回
                Thread.currentThread().interrupt();
                return StatusLimit.CANCEL;
            } catch (ExecutionException e) {
                // Redis 侧命令执行失败
                return StatusLimit.FAULT;
            }

          }
        return  StatusLimit.FAULT;
    }
    @Override
    public  <K> StatusLimit slide(int max,String destory,int window,int timeOut,K key,RedisCodec<K,String> codec){
        long nowMillis=System.currentTimeMillis();
        long windowMillis=TimeUnit.SECONDS.toMillis(window);
        String uuid=UUID.randomUUID().toString();
        try(StatefulRedisConnection<K,String> connect=redisClient.connect(codec)){
            RedisAsyncCommands<K,String> async=connect.async();
            try {
                 String [] argv={
                     String.valueOf(windowMillis),
                     String.valueOf(max),
                     String.valueOf(nowMillis),
                     String.valueOf(destory),
                     uuid
                 };
                 @SuppressWarnings("unchecked")
                 K[] keys=(K[]) new  Object[] {key};
                     RedisFuture<Long> future=async.eval(SLIDE_SCRIPT, ScriptOutputType.INTEGER, keys,argv);
                     Long allowed=future.get(timeOut,TimeUnit.SECONDS);
                     return  Long.valueOf(1L).equals(allowed)?StatusLimit.SUCCESS:StatusLimit.FAULT;
            } catch (TimeoutException e) {
                        return StatusLimit.TIMEOUT;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return StatusLimit.CANCEL;
                    } catch (ExecutionException e) {
                        return StatusLimit.FAULT;
                    }
        }
    }
}
