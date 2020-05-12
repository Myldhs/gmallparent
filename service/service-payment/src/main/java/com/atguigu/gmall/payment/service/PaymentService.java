package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-08 8:59
 */
public interface PaymentService {
    /**
     * 保存交易记录
     * @param orderInfo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    void savePaymentInfo(OrderInfo orderInfo, String paymentType);

    PaymentInfo getPaymentInfo(String outTradeNo, String name);

    void paySuccess(String outTradeNo, String name, Map<String, String> paramMap);

    void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfo);

    void closePayment(Long orderId);
}
