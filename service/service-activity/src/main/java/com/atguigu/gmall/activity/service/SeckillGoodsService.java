package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.SeckillGoods;

import java.util.List;

/**
 * @author ldh
 * @create 2020-05-11 11:22
 */
public interface SeckillGoodsService {
    /**
     * 返回全部秒杀商品列表  去redis中获取
     * @return
     */
    List<SeckillGoods> findAll();


    /**
     * 根据ID获取实体
     * @param id
     * @return
     */
    SeckillGoods getSeckillGoods(Long id);

    /**
     * 根据用户和商品ID实现秒杀下单
     * @param skuId
     * @param userId
     */
    void seckillOrder(Long skuId, String userId);

    /***
     * 根据商品id与用户ID查看订单信息  查询用户的秒杀状态
     * @param skuId
     * @param userId
     * @return
     */
    Result checkOrder(Long skuId, String userId);



}
