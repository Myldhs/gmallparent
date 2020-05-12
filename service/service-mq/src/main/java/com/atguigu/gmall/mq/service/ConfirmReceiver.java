package com.atguigu.gmall.mq.service;

import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author ldh
 * @create 2020-05-05 22:46
 */
@Component
@Configuration
public class ConfirmReceiver {

    //声明消费者 方法是消费者处理消息
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "queue.confirm",autoDelete = "false"),
            exchange = @Exchange(value = "exchange.confirm",autoDelete = "true"),
            key = {"routing.confirm"}
    ))
    public void process(Message message , Channel channel){
        // 获取里面的数据
        // 将字节数组转化为字符串
        System.out.println("接收到的消息："+ new String(message.getBody()));

        try {
            //消息处理完毕 手动确认
            // 第一个参数：long 类型的消息的Id，
            // 第二个参数是确认消息的形式：false 表示每次确认一个消息，true 表示批量确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            System.out.println("手动进行消息确认");
        } catch (IOException e) {
            System.out.println("出现异常！");
            //判断消息是否已经处理过一次(即是否已经确认) 如果消息已经处理过一次，则返回true，如果消息一次没有处理则是false。
            Boolean redelivered = message.getMessageProperties().getRedelivered();
            if(redelivered){
                System.out.println("消息已经处理过了。");
                // 表示消息不重回队列。
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            }else{
                // nack 消息接收了，但是没有正确处理。
                System.out.println("消息即将返回队列！");
                // 第三个参数表示如果消息没有正确处理，消息会再次回到消息队列。
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true);
            }
        }

    }
}
