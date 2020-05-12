package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author ldh
 * @create 2020-04-29 21:31
 */
@Component
public class AuthGlobalFilter implements  GlobalFilter {
    //引入操作redis的配置redisTemplate
    @Autowired
    private RedisTemplate redisTemplate;

    //读取在网关配置文件中配置的访问需要登录的控制器URL
    @Value("${authUrls.url}")
    private String authUrls;

    // 引入路径匹配的工具类 AntPathMatcher 可用通配符匹配的路径工具类
    private AntPathMatcher antPathMatcher = new AntPathMatcher();


    /**
     * @param exchange ServerWeb 的对象  方法进行过滤 根据请求url及是否登录进行限制访问
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 先获取用户的请求对象 http://list.gmall.com/list.html?category3Id=61
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        //对请求路径进行资源匹配  访问不同资源进行不同处理
        // 内部接口 /**/inner/** 不允许外部访问。返回提示信息  没有权限 登不登录都不行
        if(antPathMatcher.match("/**/inner/**",path)){
            //获取响应对象
            ServerHttpResponse response = exchange.getResponse();

            //带上拒绝访问提示信息（没有权限）用response进行响应 请求结束不往下进行
            return out(response, ResultCodeEnum.PERMISSION);
        }

        // 获取用户Id 判断用户是否已登录 不为空则为已登录
        String userId = getUserId(request);

        //获取临时用户id 判断是否有临时用户
        String userTempId = getUserTempId(request);


        // 判断 /api/**/auth/** 如果是这样的路径，那么应该登录才能访问{用户缓存的userId}。
        if (antPathMatcher.match("/api/**/auth/**",path)){
            //请求/api/**/auth/**下的资源 判断用户是否登录
            if (StringUtils.isEmpty(userId)){
                // 说明没有登录 获取响应对象 响应给页面提示信息 没有权限
                ServerHttpResponse response = exchange.getResponse();
                // 响应给页面提示信息 没有权限
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        // 验证用户请求的资源Url是否是限定的控制器 ，未登录情况下不允许访问的路径配置配置文件中。
        if(null !=authUrls){
            // 循环判断 authUrls = trade.html,myOrder.html,list.html
            String[] splitAuthUrls = authUrls.split(",");

            // 循环判断path 中是否包含以上请求资源 若未登录 则提示用户登录已登录 。
            for (String splitAuthUrl : splitAuthUrls) {
                //path.indexOf(splitAuthUrl)!=-1 判断path中是否包含splitAuthUrl 不包含返回-1

                if (path.indexOf(splitAuthUrl)!=-1 && StringUtils.isEmpty(userId)){
                    // 获取响应对象
                    ServerHttpResponse response = exchange.getResponse();
                    // 赋值一个状态码
                    // 303 由于请求对应的资源，存在着另一个url，重定向。
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    // 重定向到登录链接
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                    return response.setComplete();
                }
            }
        }

        // 上述验证通过 用户已登录或有用户临时id，需要将userId userTempId，传递到访问的各个微服务上。
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){
            if(!StringUtils.isEmpty(userId)){
                // 往请求头中存储一个userId
                request.mutate().header("userId",userId);
            }
            if(!StringUtils.isEmpty(userTempId)){
                // 往请求头中存储一个userTempId
                request.mutate().header("userTempId",userTempId);
            }

            // 将用户id顺着请求传递下去
            return chain.filter(exchange.mutate().request(request).build());
        }
        return chain.filter(exchange);
    }

    //输出提示信息到页面
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum){
        // 提示信息告诉用户 ,提示信息被封装到resultCodeEnum 对象
        // 将提示的信息封装到result 中(状态码 状态信息等)。
        Result<Object> result = Result.build(null, resultCodeEnum);

        //将result转化为json串  最终发到前端还要处理成一个数据缓冲流
        String resultStr = JSONObject.toJSONString(result);

        // 将resultStr 转换成一个字节数组
        byte[] bytes = resultStr.getBytes(StandardCharsets.UTF_8);
        // 声明一个DataBuffer
        DataBuffer wrap = response.bufferFactory().wrap(bytes);

        // 设置信息输入格式 UTF-8编码的json应用数据
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");

        // 将信息输入到页面
        return response.writeWith(Mono.just(wrap));
    }

    //从request中获取token，根据token从缓存中获取userId 判断用户是否已经登录
    private String getUserId(ServerHttpRequest request) {
        // 用户Id 存储在缓存 key=user:login:token value=userId
        // 必须先获取token。 token 在前端存在了两个地方可能存在header,cookie 中。
        String token = "";
        List<String> tokenList = request.getHeaders().get("token");
        if (null!=tokenList && tokenList.size()>0){
            // 我们只设置了一个token 这个集合中只有一个key ，这个key名就是 token ，值对应的也是一个。
            token=tokenList.get(0);

        }else {
            //若token中没有则动从cookie中获取
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
            //            List<HttpCookie> cookieList = cookies.get("token"); //表示获取cookie 中多个数据
            //            for (HttpCookie httpCookie : cookieList) {
            //                String value = httpCookie.getValue();
            //                // 添加到的集合中。
            //                list.add(value);
            //            }

            // 根据cookie 中的key 来获取指定数据
            HttpCookie cookie = cookies.getFirst("token");
            if (null!=cookie){
                token= URLDecoder.decode(cookie.getValue());
            }
        }
        if (!StringUtils.isEmpty(token)){
            // 才能从缓存中获取数据
            String userKey = "user:login:"+token;
            String userId = (String) redisTemplate.opsForValue().get(userKey);
            return userId;
        }
        return "";
    }


    /**
     * 在网关中获取临时用户id 临时用户id在前端被放到请求头或cookie中 key就是userTempId
     * @param request
     * @return
     */
    private String getUserTempId(ServerHttpRequest request) {
        String userTempId = "";
        List<String> tokenList = request.getHeaders().get("userTempId");
        if(null != tokenList) {
            //从缓存中获取
            userTempId = tokenList.get(0);
        } else {
            //去cookie中获取
            MultiValueMap<String, HttpCookie> cookieMultiValueMap =  request.getCookies();
            HttpCookie cookie = cookieMultiValueMap.getFirst("userTempId");
            if(cookie != null){
                userTempId = URLDecoder.decode(cookie.getValue());
            }
        }
        return userTempId;
    }



}
