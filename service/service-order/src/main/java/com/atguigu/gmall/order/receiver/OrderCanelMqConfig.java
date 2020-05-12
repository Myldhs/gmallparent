package com.atguigu.gmall.order.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * 编写取消订单的配置类
 * @author ldh
 * @create 2020-05-06 14:10
 */
//配置类 配置延迟消息队列的交换机 和延迟队列 并将队列绑定到交换机上 在消费者上只配置队列就行了
@Configuration
public class OrderCanelMqConfig {

    //声明一个队列
    @Bean
    public Queue delayQueue() {
        // 第一个参数是创建的queue的名字，第二个参数是是否支持持久化
        return new Queue(MqConst.QUEUE_ORDER_CANCEL, true);
    }

    //声明一个交换机 使用延迟插件的交换机是自定义的交换机 不是普通交换机类
    @Bean
    public CustomExchange delayExchange(){
        // 配置参数
        HashMap<String, Object> map = new HashMap<>();
        // 基于插件时，需要指定的参数，固定的用法。 交换机的类型
        map.put("x-delayed-type","direct");

        //使用插件的情况下交换机的类型就是插件名 固定的 map是设置的交换机参数使用插件的固定用法
        return new CustomExchange(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,"x-delayed-message",true,false,map);

    }

    //将队列绑定到交换机上
    @Bean
    public Binding delayBinding(){
        //返回绑定结果  构建绑定 绑定的队列         队列要绑定的交换机    使用的路由键                  没有其他参数了
        return  BindingBuilder.bind(delayQueue()).to(delayExchange()).with(MqConst.ROUTING_ORDER_CANCEL).noargs();
    }

    //配置类写完 项目启动后 spring容器中就有交换机 队列了 供监听了此队列的消费者消费消息

}
