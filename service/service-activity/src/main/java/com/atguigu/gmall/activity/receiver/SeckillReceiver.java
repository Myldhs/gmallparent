package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author ldh
 * @create 2020-05-11 9:34
 */
@Component
public class SeckillReceiver {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillGoodsService seckillGoodsService;


    //处理定时任务发送的消息 扫描秒杀商品 将其放入到redis中
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importItemToRedis(Message message, Channel channel) throws IOException {
        //获取秒杀商品集合  商品的审核状态为1 秒杀开始时间必须为当天 并且库存数量大于0
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1).gt("stock_count", 0);
        //时间比较只去年月日 时分秒就不用比较了 引入日期时间比较工具类格式化时间后 进行比较
        queryWrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(queryWrapper);

        //判断秒杀商品是否为空
        if (null != seckillGoodsList && seckillGoodsList.size() > 0) {
            // 将集合中的秒杀商品数据放入缓存中
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                // 使用hash 数据类型保存商品
                // key = seckill:goods   field = skuId  value=seckillGoods
                // 判断缓存中是否有当前key 如果有则不放入缓存
                Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());

                //判断当前秒杀中有没有当前商品
                if (flag) {
                    // 当前商品已经在缓存中有了！ 所以不需要在放入缓存！ 跳过当前循环
                    continue;
                }

                //缓存中没有数据 将当前商品放入缓存
                // 商品id为field ，对象为value 放入缓存  key = seckill:goods field = skuId value=商品字符串
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);

                //根据每一个商品的数量把商品按队列的形式放进redis中 的List中 redis中的list有队列的性质 先进先出
                //被秒杀的商品放在hash中  每个被秒杀的商品数量放入到list的中  根据redis原子性防止库存超卖
                for (Integer i = 0; i < seckillGoods.getStockCount(); i++) {
                    // key = seckill:stock:skuId
                    // lpush key value  14号商品 放出秒杀10个 那么在redis中就有一个key为seckill:stock:14的 value为10个14的list集合的数据
                    //1，如果秒杀商品有N个库存，那么我就循环往队列放入N个队列数据
                    //2，秒杀开始时，用户进入，然后就从队列里面出队，只有队列里面有数据，说明就一点有库存（redis队列保证了原子性），队列为空了说明商品售罄
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId()).leftPush(seckillGoods.getSkuId().toString());
                }

                //通知添加与更新状态位，更新为可买 redis发送seckillpush主题的消息
                // 每一个秒杀商品添加到缓存后都要发送订阅消息给其他秒杀节点初始化该商品的状态位 所有商品的状态位都保存在各个节点的内存map集合中
                redisTemplate.convertAndSend("seckillpush", seckillGoods.getSkuId() + ":1");

            }

            // 手动确认接收消息成功  秒杀商品放入缓存完毕
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

            //到目前我们就实现了商品信息导入缓存，同时更新该商品的状态位的工作

        }

    }

    /**
     * 秒杀用户加入队列后 这里消费消息 通过各种判断后将用户秒杀信息放入缓存 到这里用户才真正拥有下单资格 下单完成则秒杀成功
     * 各种判断 同一用户不能再次购买  判断状态位是否已售完 判断秒杀商品的库存
     *
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel) throws IOException {
        if (null != userRecode) {
            //Log.info("paySuccess:"+ JSONObject.toJSONString(userRecode));
            //判断秒杀商品能不能购买
            seckillGoodsService.seckillOrder(userRecode.getSkuId(), userRecode.getUserId());

            //手动确认消费消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }


    /**
     * 处理定时任务的消息 秒杀结束清空缓存
     *
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void clearRedis(Message message, Channel channel) throws IOException {
        //活动结束清空缓存
        //获取秒杀商品列表中当前秒杀已结束的商品列表
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        queryWrapper.le("end_time", new Date());
        List<SeckillGoods> list = seckillGoodsMapper.selectList(queryWrapper);

        if(!CollectionUtils.isEmpty(list)){
            //删除缓存中已结束秒杀商品
            for (SeckillGoods seckillGoods : list) {
                //删除表示商品数量的商品 List队列
                redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId());
            }
        }

        //删除缓存中该秒杀商品数据
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        //删除预下单
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);
        //删除已下单
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);


        //将该秒杀商品的数据库状态更新为结束  2  1是审核通过在秒
        SeckillGoods seckillGoodsUp = new SeckillGoods();
        seckillGoodsUp.setStatus("2");
        seckillGoodsMapper.update(seckillGoodsUp, queryWrapper);
        // 手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }
}




