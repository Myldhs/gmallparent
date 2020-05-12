package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * @author ldh
 * @create 2020-05-08 10:02
 */
@Service
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private PaymentService paymentService;

    //远程获取orderinf数据
    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private AlipayClient alipayClient;


    //创建支付宝下单（支付接口） 生成支付二维码 显示在页面上
    @Override
    public String createaliPay(Long orderId) throws AlipayApiException {
        //获取订单信息
        OrderInfo orderInfo  = orderFeignClient.getOrderInfo(orderId);
        // 保存交易记录
        paymentService.savePaymentInfo(orderInfo, PaymentType.ALIPAY.name());

        // 创建调用API对应的request 封装生产二维码的参数  不同功能创建不同请求
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();

        // 设置同步回调url
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);

        // 设置异步回调url
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址

        // 声明一个map 集合 设置生成二维码的请求参数 最后转为json
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
        map.put("total_amount",orderInfo.getTotalAmount());
        map.put("subject","test");
        //map转为json当做参数 设置生成二维码的参数
        alipayRequest.setBizContent(JSON.toJSONString(map));

        //alipayClient调用方法 根据alipayRequest生成支付二维码页面返回
        return alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单;;
    }

    //退款接口 根据orderId得到orderinfo 封装退款参数 进行退款
    @Override
    public boolean refund(Long orderId) {
        // 创建调用退款API对应的request 封装退款参数 交给alipayClient执行
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        //根据订单号查询该订单的信息
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        //封装封装退款参数先map在json
        HashMap<String, Object> map = new HashMap<>();
        //封装第三方订单交易编号
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        //封装退款金额
        map.put("refund_amount", orderInfo.getTotalAmount());
        //封装退款原因
        map.put("refund_reason", "颜色浅了点");
        //map转为json当做参数 设置请求参数
        request.setBizContent(JSON.toJSONString(map));

        //定义退款响应对象
        AlipayTradeRefundResponse response = null;
        try {
            //执行退款
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        //判断退款是否成功
        if (response.isSuccess()) {
            // 更新支付记录 ： 关闭ClOSED
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
            paymentService.updatePaymentInfo(orderInfo.getOutTradeNo(), paymentInfo);
            return true;
        } else {
            return false;
        }
    }

    /**
     * checkPayment方法只查询支付宝中是否有交易记录 只要有交易记录就返回true  然后才能再去关闭交易 未付款关闭成功 已付款关闭失败
     * 要用户扫描了二维码那么不管有没有付钱支付宝中都会有交易记录
     * @param orderId
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean checkPayment(Long orderId) {
        // 根据订单Id 查询订单信息
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //创建查询请求对象
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        //封装查询请求参数
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        // 设置查询请求参数  根据out_trade_no 查询交易记录
        request.setBizContent(JSON.toJSONString(map));

        //执行查询请求
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        //判断查询是否成功
        if(response.isSuccess()){
            return true;
        } else {
            return false;
        }
    }

    /**
     * 关闭支付宝过期交易记录
     * 当用户点击支付方式 生成支付二维码时 PaymentInfo表中插入一条数据，只下单不生成二维码不往PaymentInfo表中插入一条数据
     * 用户扫码之后 会在支付宝中产生交易记录数据我们还要调用支付宝与微信关闭交易接口，把对应的交易关闭，防止我们取消了订单，
     * 他还能支付回来，未付款关闭成功 已付款关闭失败
     * 关闭订单 订单表一定修改 支付记录表和支付宝看支付到哪一步 有了产生数据就修改
     * @param orderId
     */
    @SneakyThrows
    @Override
    public Boolean closePay(Long orderId) {
        //通过orderId获取orderInfo
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        //创建支付宝关闭交易记录请求对象
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

        //封装关闭交易记录参数为map
        HashMap<String, Object> map = new HashMap<>();
        // map.put("trade_no",paymentInfo.getTradeNo()); // 从paymentInfo 中获取！
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("operator_id","YX01");//不写也行

        //往请求中设置参数 参数map转为json
        request.setBizContent(JSON.toJSONString(map));

        //发送请求得到响应
        AlipayTradeCloseResponse response = alipayClient.execute(request);

        //判断关闭是否成功
        if(response.isSuccess()){
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }


}
