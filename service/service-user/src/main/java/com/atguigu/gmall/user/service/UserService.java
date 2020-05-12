package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserInfo;

/**
 * @author ldh
 * @create 2020-04-29 11:51
 */
public interface UserService {
    /**
     * 用户登录方法  注意密码是加密的
     * @param userInfo
     * @return
     */
    UserInfo login(UserInfo userInfo);

}
