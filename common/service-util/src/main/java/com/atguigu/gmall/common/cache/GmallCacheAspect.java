package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author ldh
 * @create 2020-04-24 21:38
 */
@Component //声明该类是个组件 装配到容器中
@Aspect //声明该类是个切面类  利用aop实现 往方法上打上注解就对该方法实现缓存 注解就是aop的切入点表达式
public class GmallCacheAspect {

    // 引入redis，redissonClient

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 编写一个方法 利用环绕通知来获取对应的数据
    // 模拟@Transactional 用法
    // 返回值类型Object 因为我们在切方法的时候，不能确定方法的返回值到底是什么？
    //  SkuInfo getSkuInfo(Long skuId) 返回是SkuInfo
    //  BigDecimal getSkuPrice(Long skuId) 返回的BigDecimal
    //环绕通知 在通知方法中执行接入点方法 执行前的部分可以为前置通知 等等
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice (ProceedingJoinPoint point){
        /*
            1.  获取接入点参数列表（接入点就是打上注解的方法）
            2.  获取方法上的注解
            3.  获取前缀
            4.  获取目标方法的返回值
         */

        // 1、声明一个Object最终返回结果对象
        Object result = null;

        //2、获取接入点参数列表
        // 以后缓存的key 形式 sku:[22] sku:[skuId]
        Object[] args = point.getArgs();

        //3、获取接入点的方法签名
        MethodSignature methodSignature = (MethodSignature) point.getSignature();

        //4、获取方法上的注解@GmallCache
        GmallCache gmallCache = methodSignature.getMethod().getAnnotation(GmallCache.class);

        //5、获取注解中的前缀值
        String prefix = gmallCache.prefix();

        // 6、组成缓存数据的key sku:[22] sku:[skuId]
        String key = prefix + Arrays.asList(args).toString();

        //7、去缓存中查询数据 (查询的就是最后返回结果)
        result = cacheHit(key, methodSignature);

        //8、判断缓存中是否有数据
        if(result!=null){
            //获取到数据直接返回
            return result;
        }

        //9、没获取到数据去数据库中查  上锁 分布式锁
        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            //加锁 获取分布式锁
            boolean isLock = lock.tryLock(100, 10, TimeUnit.SECONDS);
            //判断是否获得锁
            if(isLock){
                try {
                    // 获取到分布式锁，从数据库中获取数据 执行接入点方法
                    result = point.proceed(point.getArgs());
                    //判断是否从数据库中获取到了数据 是否为null 防止缓存穿透
                    if(null==result){
                        //返回一个空对象 并将这个空对象转化为json字符串放入缓存中
                        Object  object = new Object();
                        redisTemplate.opsForValue().set(key,JSONObject.toJSONString(object),RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        return object;
                    }
                    //如果对象不为空，则将这个对象转化为json字符串放入缓存中 然后返回
                    redisTemplate.opsForValue().set(key,JSONObject.toJSONString(result),RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    return  result;
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }

            }else{
                //未获得锁 等待 然后重新去缓存中查询
                Thread.sleep(1000);
                return cacheHit(key, methodSignature);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //解锁
            lock.unlock();
        }

        //10、返回最终查询结果
        return result;
    }

    // 提取出的方法 从缓存获取数据 返回值为Object 可能从缓存中取到任意类型的数据
    private Object cacheHit(String key,MethodSignature methodSignature) {

        // 必须有key | 将数据放入缓存时是以json形式存放的
        // 如果是访问的skuId 在数据库中不存在，空的Object
        String cache = (String) redisTemplate.opsForValue().get(key);

        //判断从否从缓存中获取到了数据
        if(StringUtils.isNotBlank(cache)){
            //aop为通用方法 任何方法都可使用 从缓存中获取到的数据类型为他们方法的返回值类型（就是他们的目的数据）
            //将cache字符串转换为返回类型对象 有数据就转成有数据的对象，没数据就转换成空对象返回

            // 我们要确定缓存数据字符串是项目中那种数据类型
            //  SkuInfo getSkuInfo(Long skuId) 返回是SkuInfo
            //  BigDecimal getSkuPrice(Long skuId) 返回的BigDecimal
            //  总结：方法的返回值类型是什么，那么缓存就是存储的什么数据类型！
            //获取返回值的类型 数据放入缓存时是以json字符串存放的
            Class returnType = methodSignature.getReturnType();

            //将结果转为返回值类型
            return JSONObject.parseObject(cache,returnType);
        }
        //cache为null没有从缓存中获取到数据
        return null;
    }

}
