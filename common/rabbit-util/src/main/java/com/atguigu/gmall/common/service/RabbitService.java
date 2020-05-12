package com.atguigu.gmall.common.service;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author ldh
 * @create 2020-05-05 22:35
 */
@Service
public class RabbitService {

    // 引入操作消息队列的模板
    @Autowired
    private RabbitTemplate rabbitTemplate;


    /**
     *  发送消息  发送普通消息
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息
     */
    public boolean sendMessage(String exchange, String routingKey, Object message){
        rabbitTemplate.convertAndSend(exchange,routingKey,message);
        //没有出错就返回true
        return true;
    }

    /**
     * 利用插件 发送延迟消息
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息
     * @param delayTime 单位：秒 延迟时间
     */
    public boolean sendDelayMessage(String exchange, String routingKey, Object message, int delayTime){
        rabbitTemplate.convertAndSend(exchange, routingKey, message, new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                //设置消息延迟时间 delayTime*1000传进来是秒  但是这里参数是毫秒 所以乘1000
                message.getMessageProperties().setDelay(delayTime*1000);
                return message;
            }
        });
        return true;
    }

}
