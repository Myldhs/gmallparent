package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ldh
 * @create 2020-04-29 21:18
 */
@Controller
public class PassportController {

    /**
     *首页点击登录后渲染登录页面
     * @return
     */
    @GetMapping("login.html")
    public String login(HttpServletRequest request) {
        //记录登录页面的前一个页面url（即从那个页面发起的登录 发送到前端登陆成功后跳转到该页面，没有就跳转首页）
        String originUrl = request.getParameter("originUrl");
        request.setAttribute("originUrl",originUrl);
        return "login";
    }
}
