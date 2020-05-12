package com.atguigu.gmall.payment.client;

import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.client.impl.PaymentFeignClientImpl;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author ldh
 * @create 2020-05-09 15:24
 */
@FeignClient(value = "service-payment",fallback = PaymentFeignClientImpl.class)
public interface PaymentFeignClient {
    //根据订单id 去支付宝中查看是否有交易记录
    //checkPayment方法只查询支付宝中是否有交易记录 只要有交易记录就返回true  然后才能再去关闭交易 未付款关闭成功 已付款关闭失败
    // 只要用户扫描了二维码那么不管有没有付钱支付宝中都会有交易记录
    @RequestMapping("/api/payment/alipay/checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId);

    //通过outTradeNo 第三方支付编号 去数据库查询是否有交易记录
    @GetMapping("/api/payment/alipay/getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo);

    //根据orderId 关闭支付宝中的交易记录
    @GetMapping("/api/payment/alipay/closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId);


}
