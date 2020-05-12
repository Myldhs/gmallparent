package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-08 9:00
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RabbitService rabbitService;

    /**
     * 保存交易记录  本质就是往`payment_info表中插入一条记录
     * @param orderInfo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        //查询payment_info表 看表中是否已有交易记录  通过order_id和payment_type来确定一条记录
        //同一订单只能在表中出现一次 同一订单  保证支付幂等性 多人支付下 只能有一个人支付成功
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderInfo.getId());
        queryWrapper.eq("payment_type", paymentType);
        Integer count = paymentInfoMapper.selectCount(queryWrapper);
        if(count > 0) return;

        //创建插入的PaymentInfo对象并赋值
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());//刚进入都是支付中
        paymentInfo.setSubject(orderInfo.getTradeBody());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());

        //插入数据
        paymentInfoMapper.insert(paymentInfo);
    }

    //根据out_trade_no 和付款方式查询支付记录数据
    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo, String name) {
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo).eq("payment_type",name);
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(paymentInfoQueryWrapper);

        return paymentInfo;

    }

    /**
     * 支付成功后方法 正常的支付成功，我们应该更新交易记录状态
     * @param outTradeNo 第三方支付编号
     * @param name  支付方式
     * @param paramMap 异步回调参数
     * */
    @Override
    public void paySuccess(String outTradeNo, String name, Map<String, String> paramMap) {
        // update paymentInfo set PaymentStatus = PaymentStatus.PAID ,CallbackTime = new Date() where out_trade_no = ?
        //创建更新对象
        PaymentInfo paymentInfoUpd = new PaymentInfo();

        //设置更新支付状态为已支付
        paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID.name());
        //设置更新异步回调时间
        paymentInfoUpd.setCallbackTime(new Date());
        //设置更新异步回调参数
        paymentInfoUpd.setCallbackContent(paramMap.toString());

        //更新trade_no
        paymentInfoUpd.setTradeNo(paramMap.get("trade_no"));

        //更新支付记录 out_trade_no = outTradeNo 记录
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("out_trade_no", outTradeNo);
        paymentInfoMapper.update(paymentInfoUpd,queryWrapper);

        //根据第三方支付编号查询支付信息 得到订单id
        PaymentInfo paymentInfo = getPaymentInfo(outTradeNo, name);
        // 支付成功后更新订单状态！ 使用消息队列通知订单模块修改该订单状态
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY,paymentInfo.getOrderId());

    }

    //根据第三方交易编号outTradeNo更新支付状态
    @Override
    public void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfo) {
        //更新支付记录 out_trade_no = outTradeNo 记录
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("out_trade_no", outTradeNo);
        paymentInfoMapper.update(paymentInfo,queryWrapper);
    }



    /**
     * 关闭过期支付记录 PaymentInfo
     * 当用户点击支付方式 生成支付二维码时 PaymentInfo表中插入一条数据，只下单不生成二维码不往PaymentInfo表中插入一条数据
     * 用户扫码之后 会在支付宝中产生交易记录数据我们还要调用支付宝与微信关闭交易接口，把对应的交易关闭，防止我们取消了订单，他还能支付回来，
     * 关闭订单 订单表一定修改 支付记录表和支付宝看支付到哪一步 有了产生数据就修改
     * @param orderId
     */
    @Override
    public void closePayment(Long orderId) {
        //更新PaymentInfo的支付状态
        // 设置关闭支付记录的条件
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderId);
        Integer count = paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        // 判断当前支付记录是否存在 ，如果当前的支付记录不存在，则不更新支付记录
        if (null == count || count.intValue()==0) return;

        // 在关闭支付宝交易之前。先关闭paymentInfo支付记录 修改支付状态
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);

    }


}

