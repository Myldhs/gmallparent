package com.atguigu.gmall.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * @author ldh
 * @create 2020-05-12 11:55
 */
@Configuration
public class KeyResolverConfig {

    //按照ip限流 获取请求用户ip作为限流key。
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getHostName());
    }

    /*//按照用户限流 获取请求用户token作为限流key。
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getHeaders().get("token").get(0));
    }

    //按照接口URL限流  获取请求地址的uri作为限流key
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getPath().value());
    }*/

}
