package com.atguigu.gmall.list.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.list.service.SearchService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author ldh
 * @create 2020-05-06 9:19
 */
@Component
public class ListReceiver {

    @Autowired
    private SearchService searchService;

    /**
     * 上架有上架的队列 上架消息发送到上架队列 两个消费者分别监听不同的队列
     * 消费消息 商品上架 上架到es  使用注解监听消息队列
     * 生产者发送的消息可以直接以消息名进行映射接收（Long skuId,），不用再使用Message获取
     * @param skuId
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_UPPER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_GOODS_UPPER}
    ))
    public void upperGoods(Long skuId, Message message, Channel channel) throws IOException {
        if (null != skuId) {
            //接收到消息 调用服务层进行商品上架  将数据从MySQL->es
            searchService.upperGoods(skuId);
        }
        //手动确认 ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }


    /**
     * 下架有下架的队列 下架消息发送到下架队列 两个消费者分别监听不同的队列
     * 消费消息 商品下架 从es中下架
     * 生产者发送的消息可以直接以消息名进行映射接收（Long skuId,），不用再使用Message获取
     * @param skuId
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_LOWER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_GOODS_LOWER}
    ))
    public void lowerGoods(Long skuId, Message message, Channel channel) throws IOException {
        if (null != skuId) {
            //接收到消息 调用服务层进行商品下架
            searchService.lowerGoods(skuId);
        }
        //手动确认 ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}



