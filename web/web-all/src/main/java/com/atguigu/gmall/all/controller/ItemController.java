package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.ItemFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * @author ldh
 * @create 2020-04-22 11:15
 */
@Controller
@RequestMapping
public class ItemController {

    //注入ItemFeignClient 调用service-item模块
    @Autowired
    private ItemFeignClient itemFeignClient;

    /**
     * 根据前台页面传过来的skuId 调用service-item模块 获取数据渲染sku商品详情页面
     * @param skuId
     * @param model
     * @return
     */
    @RequestMapping("{skuId}.html")
    public String getItem (@PathVariable("skuId") Long skuId, Model model){

        //根据前台页面传过来的skuId 调用service-item模块 获取数据渲染sku商品详情页面
        Result<Map> result = itemFeignClient.getItem(skuId);

        //将获得的数据传到前端页面
        model.addAllAttributes(result.getData());

        return "item/index";
    }





}
