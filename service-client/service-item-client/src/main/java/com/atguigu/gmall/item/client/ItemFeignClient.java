package com.atguigu.gmall.item.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.impl.ItemDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author ldh
 * @create 2020-04-22 11:10
 */
@FeignClient(value = "service-item", fallback = ItemDegradeFeignClient.class)
public interface ItemFeignClient {
    /**
     * 调用service-item模块 我们需要将数据封装到map中返回给前端。
     * map.put("price","商品的价格")
     * map.put("skuInfo","skuInfo数据")
     * 根据skuId 调用service-product模块，获取sku详情信息
     * @param skuId
     * @return
     */
    @GetMapping("/api/item/{skuId}")
    public Result getItem(@PathVariable("skuId") Long skuId);

}
