package com.atguigu.gmall.common.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ldh
 * @create 2020-04-24 21:35
 */
@Target(ElementType.METHOD) //此注解只能用在方法上
@Retention(RetentionPolicy.RUNTIME) //作用范围最大到程序运行时
public @interface GmallCache {
    /**
     * 在注解中定义缓存key的前缀字段属性 默认值是cache
     * @return
     */
    String prefix() default "cache";

}
