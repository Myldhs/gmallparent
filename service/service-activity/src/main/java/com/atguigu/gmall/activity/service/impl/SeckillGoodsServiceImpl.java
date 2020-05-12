package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author ldh
 * @create 2020-05-11 11:24
 */
@Service
@Transactional
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 返回全部秒杀商品列表  去redis中获取
     * @return
     */
    @Override
    public List<SeckillGoods> findAll() {
        List<SeckillGoods> seckillGoodsList = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
        return seckillGoodsList;

    }

    /**
     * 根据skuID去redis中获取单个秒杀实体
     * @param id
     * @return
     */
    @Override
    public SeckillGoods getSeckillGoods(Long id) {
        return (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(id.toString());
    }

    /**
     * 根据用户和商品ID通过各种判断后实现秒杀下单
     * 秒杀用户加入队列后 这里消费消息 通过各种判断后将用户秒杀信息放入缓存 到这里用户才真正拥有下单资格 下单完成则秒杀成功
     * 各种判断 同一用户不能再次购买  判断状态位是否已售完 判断秒杀商品的库存
     * @param skuId
     * @param userId
     */
    @Override
    public void seckillOrder(Long skuId, String userId) {
        /**
         * 监听用户
         * 判断状态位
         * 判断库存
         * 放入缓存
         * */
        //获取秒杀商品状态位， 1：可以秒杀 0：秒杀结束 判断当前商品是否还可以秒杀
        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)) {
            //已售罄
            return;
        }

        //如何保证用户不能抢多次 如果第一次抢到了则会讲抢购信息保存在缓存中 判断redis中key是否存在即可判断当前用户是否已经抢过了
        String userSeckillKey = RedisConst.SECKILL_USER + userId; 
        //key存在则不会添加成功
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(userSeckillKey, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if (!isExist) {
            return;
        }

        //获取redis中List队列中的商品，如果能够获取，则商品存在，可以下单               吐出来一个商品id
        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if (StringUtils.isEmpty(goodsId)) {
            //商品售罄，redis发布主题消息 其他秒杀节点更新此秒杀商品的状态位（内存中的map）
            redisTemplate.convertAndSend("seckillpush", skuId + ":0");
            //已售罄
            return;
        }

        //全部通过判断 生成订单记录 保存在redis
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(this.getSeckillGoods(skuId));
        orderRecode.setNum(1);
        //生成下单码
        orderRecode.setOrderStr(MD5.encrypt(userId+skuId));

        //订单数据存入Reids  此方法两次放入缓存数据 是不同的  存放在这个缓存Hash中的才是真正可以下单购买秒杀商品的 抢单成功
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(), orderRecode);

        //更新数据库的库存
        updateStockCount(orderRecode.getSeckillGoods().getSkuId());
    }


    /**
     * 更新秒杀商品库存
     * @param skuId
     */
    private void updateStockCount(Long skuId) {
        //库存在缓存中有一份 在数据库中有一份 更新数据库中是数据以缓存中的为准  获取缓存中list中的数量
        Long stockCount = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();

        //其数量对2取余 目的是不频繁的操作数据库
        if (stockCount % 2 == 0) {
            //售出商品 库存数据同步到数据库
            SeckillGoods seckillGoods = getSeckillGoods(skuId);
            seckillGoods.setStockCount(stockCount.intValue());
            seckillGoodsMapper.updateById(seckillGoods);

            //更新缓存 更新缓存中秒杀商品列表中该商品的库存
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
        }

    }


    /***
     * 根据商品id与用户ID查看订单信息  查询用户的秒杀状态
     * @param skuId
     * @param userId
     * @return
     */
    @Override
    public Result checkOrder(Long skuId, String userId) {
        // 判断当前用户是否存在 用户不能购买两次  存在要么已购买成功 要么还没购买但有机会只要有库存就可以
        //用户能不能抢单
        boolean isExist =redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if (isExist) {
            //判断该用户的订单是否存在  抢单Hash
            boolean isHasKey = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if (isHasKey) {
                //订单存在  抢单成功 抢到了购买资格 可以下单买
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                //秒杀（抢单）成功！ 返回 秒杀信息对象 和 状态码
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }

        //判断用户是否已经下过订单 不是第一次抢 下过单了  秒杀订单已经产生（有过期时间的订单） 只不过还没付钱
        // 在saveOrder方法中往seckill:orders:users保存数据
        boolean isExistOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if(isExistOrder) {
            //用户已下过订单
            String orderId = (String)redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            //返回 订单id 和状态码
            return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }

        //判断秒杀商品的状态位 是否还能继续秒杀
        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)) {
            //已售罄 抢单失败
            return Result.build(null, ResultCodeEnum.SECKILL_FAIL);
        }

        //不是在前面的所有状态那就是还在 正在排队中 还在mq中
        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }
}
