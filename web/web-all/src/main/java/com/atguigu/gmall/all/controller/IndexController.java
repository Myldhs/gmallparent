package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ldh
 * @create 2020-04-26 20:27
 */
@Controller
@RequestMapping
public class IndexController {

    //自动注入调用商品数据模块客户端
    @Autowired
    private ProductFeignClient productFeignClient;

    /**
     * 首页 方式二：分类数据缓存，动态渲染
     * @param request
     * @return
     */
    @GetMapping({"/", "index.html"})
    public String index(HttpServletRequest request) {
        Result result = productFeignClient.getBaseCategoryList();
        request.setAttribute("list", result.getData());
        return "index/index";
    }


}
