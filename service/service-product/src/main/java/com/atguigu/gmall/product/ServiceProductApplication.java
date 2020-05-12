package com.atguigu.gmall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author ldh
 * @create 2020-04-17 16:17
 */
@SpringBootApplication
@SpringBootConfiguration
@ComponentScan({"com.atguigu.gmall"})
@MapperScan({"com.atguigu.gmall.product.mapper"})
@EnableDiscoveryClient //开启nacos服务注册
@EnableTransactionManagement //开启声明式事务注解
public class ServiceProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceProductApplication.class,args);
    }
}
