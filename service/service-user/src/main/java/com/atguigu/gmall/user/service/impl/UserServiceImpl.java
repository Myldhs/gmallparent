package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * @author ldh
 * @create 2020-04-29 11:53
 */
@Service
public class UserServiceImpl  implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 用户登录方法  注意密码是加密的
     * @param userInfo
     * @return
     */
    @Override
    public UserInfo login(UserInfo userInfo) {
        // select * from userInfo where userName = ? and passwd = ?
        //去数据库进行用户名密码校验  密码是加密的
        //获取加密后的密码
        String newPasswd  = DigestUtils.md5DigestAsHex(userInfo.getPasswd().getBytes());

        //创建查询条件
        QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("login_name",userInfo.getLoginName());
        wrapper.eq("passwd",newPasswd );

        //进行查询
        UserInfo userInfo1 = userInfoMapper.selectOne(wrapper);

        //判断是否查询成功
        if(null != userInfo1){
            return userInfo1;
        }else{
            return null;
        }
    }
}
