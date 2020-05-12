package com.atguigu.gmall.item.service;

import java.util.Map;

/**
 * @author ldh
 * @create 2020-04-21 19:52
 */
public interface ItemService {
    /**
     * 根据skuId 调用service-product模块，获取sku详情信息
     * @param skuId
     * @return
     */
    Map<String, Object> getBySkuId(Long skuId);

}
