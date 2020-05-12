package com.atguigu.gmall.list.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.impl.ListDegradeFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;

/**
 * @author ldh
 * @create 2020-04-27 17:07
 */
@FeignClient(value = "service-list", fallback = ListDegradeFeignClient.class)
public interface ListFeignClient {

    /**
     * 根据商品的skuId 更新商品热点数据  暂存在redis中 每访问一次加一 达到 一定数值去es进行更新操作
     * @param skuId
     */
    @GetMapping("/api/list/inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable("skuId") Long skuId);

    /**
     * 首页搜索商品 通过关键字 三级分类  品牌  平台属性筛选 使用json 传值，接收，将json转化为java 对象
     * @param searchParam
     * @return
     * @throws IOException
     */
    @PostMapping("/api/list")
    public Result list(@RequestBody SearchParam searchParam);

    /**
     * 上架商品
     * @param skuId
     * @return
     */
    @GetMapping("/api/list/inner/upperGoods/{skuId}")
    Result upperGoods(@PathVariable("skuId") Long skuId);

    /**
     * 下架商品
     * @param skuId
     * @return
     */
    @GetMapping("/api/list/inner/lowerGoods/{skuId}")
    Result lowerGoods(@PathVariable("skuId") Long skuId);



}
