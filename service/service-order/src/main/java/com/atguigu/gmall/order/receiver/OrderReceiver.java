package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 接受到取消订单的延迟消息的处理类 消费消息的类
 * @author ldh
 * @create 2020-05-07 20:39
 */
@Component
public class OrderReceiver {

    //调用orderService中的方法 根据订单号查询订单信息 （不需要为订单明细集合赋值）
    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;


    /**
     * 声明取消订单的消费者
     * 延迟队列，在这里不用做交换机与队列绑定 （因为交换机和队列及其绑定在配置类已经做好了 这里直接确定监听的是哪个队列就行了）
     * 接收消息可以用数据直接映射接收
     * 关闭订单 订单 交易记录 支付宝 判断时从外向内判断  关闭时由内向外关闭
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        //判断orderId是否为空
        if(null!=orderId){
            // 根据orderId获取当前的订单orderInfo
            OrderInfo orderInfo= orderService.getById(orderId);

            //判断当前订单是否存在 只有当前订单存在 并且当前订单状态为未付款才关闭订单表
            if(null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){

                //订单存在 先查看支付记录表中是否有相关数据 因为正常支付成功流程先修改的是支付记录表
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                if(null != paymentInfo && paymentInfo.getPaymentStatus().equals(PaymentStatus.UNPAID.name())){
                    //只有支付记录存在 并且支付状态为未付款才关闭 paymentInfo支付记录 （点击了选择支付方式）

                    //查看支付宝中是否有交易记录 支付宝中有交易记录（扫过码）才关闭支付宝交易记录
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    if(flag){
                        //支付宝中有交易记录 （扫过码） 关闭支付宝交易记录
                        Boolean result = paymentFeignClient.closePay(orderId);
                        //判断关闭支付宝交易记录是否成功
                        if(result){
                            //关闭成功 用户未付款  下面关闭交易记录表及订单表。 2是关不关闭支付记录表标志
                            orderService.execExpiredOrder(orderId,"2");
                        }else{
                            //关闭失败用户已付款  进入正常支付成功后流程
                            // 支付成功后更新订单状态！ 使用消息队列通知订单模块修改该订单状态
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,paymentInfo.getOrderId());
                        }
                    }else{
                        //支付宝中没有交易记录 用户生成了二维码，但是没有扫描。 关闭支付状态表 关闭订单表
                        orderService.execExpiredOrder(orderId,"2");
                    }
                }else{
                    //生成订单 但是没有点击选择支付方式生成支付二维码  关闭订单表 1是不关闭交易记录表
                    orderService.execExpiredOrder(orderId,"1");
                }
            }
            //手动确认消息以处理  消息id       一次确认一条
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }

    }

    /**
     * 订单支付成功后，监听队列，获取消息，更改订单状态与通知扣减库存
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId, Message message, Channel channel) throws IOException {
        //判断消息是否为空
        if(null!=orderId){
            //根据订单id 获取订单信息
            OrderInfo orderInfo = orderService.getById(orderId);

            //判断orderInfo是否存在及支付状态是不是未支付
            if(null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                // 支付成功！ 修改订单状态为已支付
                orderService.updateOrderStatus(orderId, ProcessStatus.PAID);

                //发送消息给库存 商品减库存
                orderService.sendOrderStatus(orderId);
            }
        }
        //手动通知 消息确认消费
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }

    /**
     * 扣减库存成功，更新订单状态  库存系统发送消息  在这接收处理  传过来的消息时json格式 orderId  status
     * @param msgJson
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson, Message message, Channel channel) throws IOException {
        //判断传输过来的msgJson是否为空
        if (!StringUtils.isEmpty(msgJson)){

            //不为空将json转为map
            Map<String,Object> map = JSON.parseObject(msgJson, Map.class);
            //取出消息中的参数
            String orderId = (String)map.get("orderId");
            String status = (String)map.get("status");
            //判断减库存状态
            if ("DEDUCTED".equals(status)){
                // 减库存成功！ 修改订单状态为待发货
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.WAITING_DELEVER);
            }else {
        /*
            减库存失败！远程调用其他仓库查看是否有库存 补货！ 若补货失败则人工客服与客户沟通  库存异常
            true:   orderService.sendOrderStatus(orderId); orderService.updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);
            false:  1.  补货  | 2.   人工客服。
         */
                //修改订单状态为库存异常
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.STOCK_EXCEPTION);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }







    }
