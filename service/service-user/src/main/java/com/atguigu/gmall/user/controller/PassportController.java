package com.atguigu.gmall.user.controller;

import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author ldh 用户登录认证接口
 * @create 2020-04-29 14:01
 */
@RestController
@RequestMapping("/api/user/passport")
public class PassportController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 因为login.html 登录方法，登录方法提交的数据 this.user {json 数据}
     * 用户登录
     * @param userInfo
     * @return
     *///
    @PostMapping("login")//前端传过来的是一个json化的userInfo对象 只有用户名和密码
    public Result login(@RequestBody UserInfo userInfo) {
        //进行登录信息的数据库认证 该用户是否已经注册
        UserInfo info = userService.login(userInfo);

        //判断info是否为空
        if (info != null) {
            //利用UUID创建为该用户创建唯一token
            String token = UUID.randomUUID().toString().replaceAll("-", "");

            //创建保存用户信息的map name nickName用与页面用户信息展示 返回token保存在cookie和请求头中
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", info.getName());
            map.put("nickName", info.getNickName());
            map.put("token", token);

            //将token和用户id保存在redis中作为用户已登录的标志 定义 key=user:login:token value = userId
            redisTemplate.opsForValue().set(RedisConst.USER_LOGIN_KEY_PREFIX + token, info.getId().toString(), RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);
            return Result.ok(map);
        } else {
            return Result.fail().message("用户名或密码错误");
        }
    }

    /**
     * 退出登录 只需要清除redis中的用户数据即可
     * @param request
     * @return
     */
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        // 因为缓存中存储用户数据的时候，需要token，所以删除的时候，需要token组成key
        // 当登录成功之后，token 放入了，cookie，header中。
        // 从heaer 中获取token  清除数据
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + request.getHeader("token"));
        return Result.ok();
    }



}
