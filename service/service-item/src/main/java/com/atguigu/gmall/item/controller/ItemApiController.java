package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author ldh
 * @create 2020-04-21 19:48
 */
@Api(tags = "前台商品数据获取")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/api/item") //给各个平台方调用获取商品数据的
public class ItemApiController {

    @Autowired
    private ItemService itemService;

    /**
     * 我们需要将数据封装到map中返回给前端。
     * map.put("price","商品的价格")
     * map.put("skuInfo","skuInfo数据")
     * 根据skuId 调用service-product模块，获取sku详情信息
     * @param skuId
     * @return
     */
    @ApiOperation("根据skuId获取sku详情信息")
    @GetMapping("{skuId}")
    public Result getItem(@PathVariable("skuId") Long skuId){
        Map<String, Object> resultMap = itemService.getBySkuId(skuId);

        return Result.ok(resultMap);

    }

}
