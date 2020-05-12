package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

/**
 * @author ldh
 * @create 2020-05-08 10:01
 */
public interface AlipayService {
    //创建支付宝下单
    String createaliPay(Long orderId) throws AlipayApiException;

    boolean refund(Long orderId);

    /***
     * 关闭交易
     * @param orderId
     * @return
     */
    Boolean closePay(Long orderId);

    /**
     * 根据订单主动去支付宝查询是否支付成功！
     * @param orderId
     * @return
     */
    Boolean checkPayment(Long orderId);

}
