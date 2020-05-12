package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-08 10:03
 */
@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;

    //生成支付二维码 直接返回到页面
    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable(value = "orderId") Long orderId, HttpServletResponse response) {
        String from = "";
        try {
            //调用方法生成二维码
            from = alipayService.createaliPay(orderId);

        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        //返回到页面
        return from;
    }

    /**
     * 支付宝支付后同步回调   同步回调到这个控制器  然后重定向到网站支付成功页  让用户继续操作
     * @return
     */
    @RequestMapping("/callback/return")
    public String callBack() {
        // 同步回调给用户展示信息  重定向到支付成功页  http://payment.gmall.com/pay/success.html
        return "redirect:" + AlipayConfig.return_order_url;
    }

    /**
     * 支付宝异步回调处  必须使用内网穿透  返回也是返回给支付宝  判定通过后修改支付记录中的支付状态为已支付
     * 将异步通知中收到的所有参数存放在map中
     * @param paramMap
     * @return
     */
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap){
        //将参数处理后发送给支付宝做初步验签 返回布尔值
        boolean signVerified = false; //调用SDK验证签名
        try {
            //异步返回结果的验签的前四步
            /**
             *https://商家网站通知地址?voucher_detail_list=[{"amount":"0.20","merchantContribute":"0.00","name":"5折券","otherContribute":"0.20","type":"ALIPAY_DISCOUNT_VOUCHER","voucherId":"2016101200073002586200003BQ4"}]&fund_bill_list=[{"amount":"0.80","fundChannel":"ALIPAYACCOUNT"},{"amount":"0.20","fundChannel":"MDISCOUNT"}]&subject=PC网站支付交易&trade_no=2016101221001004580200203978&gmt_create=2016-10-12 21:36:12&notify_type=trade_status_sync&total_amount=1.00&out_trade_no=mobile_rdm862016-10-12213600&invoice_amount=0.80&seller_id=2088201909970555&notify_time=2016-10-12 21:41:23&trade_status=TRADE_SUCCESS&gmt_payment=2016-10-12 21:37:19&receipt_amount=0.80&passback_params=passback_params123&buyer_id=2088102114562585&app_id=2016092101248425&notify_id=7676a2e1e4e737cff30015c4b7b55e3kh6& sign_type=RSA2&buyer_pay_amount=0.80&sign=***&point_amount=0.00
             * 第一步： 在通知返回参数列表中，除去 sign、sign_type 两个参数外，凡是通知返回回来的参数皆是待验签的参数。
             * 第二步： 将剩下参数进行 url_decode，然后进行字典排序，组成字符串，得到待签名字符串：
             * app_id=2016092101248425&buyer_id=2088102114562585&buyer_pay_amount=0.80&fund_bill_list=[{"amount":"0.80","fundChannel":"ALIPAYACCOUNT"},{"amount":"0.20","fundChannel":"MDISCOUNT"}]&gmt_create=2016-10-12 21:36:12&gmt_payment=2016-10-12 21:37:19&invoice_amount=0.80&notify_id=7676a2e1e4e737cff30015c4b7b55e3kh6&notify_time=2016-10-12 21:41:23&notify_type=trade_status_sync&out_trade_no=mobile_rdm862016-10-12213600&passback_params=passback_params123&point_amount=0.00&receipt_amount=0.80&seller_id=2088201909970555&subject=PC网站支付交易&total_amount=1.00&trade_no=2016101221001004580200203978&trade_status=TRADE_SUCCESS&voucher_detail_list=[{"amount":"0.20","merchantContribute":"0.00","name":"5折券","otherContribute":"0.20","type":"ALIPAY_DISCOUNT_VOUCHER","voucherId":"2016101200073002586200003BQ4"}]
             * 第三步： 将签名参数（sign）使用 base64 解码为字节码串。
             * 第四步： 使用 RSA 的验签方法，通过签名字符串、签名参数（经过 base64 解码）及支付宝公钥验证签名。
             * */
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        //获取支付包的异步回调参数中的相关参数值
        String trade_status = paramMap.get("trade_status");
        String out_trade_no = paramMap.get("out_trade_no");


        /**
         * 第五步：需要严格按照如下描述校验通知数据的正确性：
         * 商户需要验证该通知数据中的 out_trade_no 是否为商户系统中创建的订单号；
         * 判断 total_amount 是否确实为该订单的实际金额（即商户订单创建时的金额）；
         * 校验通知中的 seller_id（或者 seller_email) 是否为 out_trade_no 这笔单据的对应的操作方（有的时候，一个商户可能有多个 seller_id/seller_email）；
         * 验证 app_id 是否为该商户本身。
         * */
        //判断初步验签是否通过 通过进行第五步校验
        if(signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中给支付宝返回success并继续商户自身业务处理，校验失败返回failure
            //在支付宝的业务通知中，只有交易通知状态为 TRADE_SUCCESS 或 TRADE_FINISHED 时，支付宝才会认定为买家付款成功。
            if("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                //支付成功 更改支付状态 只有支付状态是未付款 才改变 改为已付款
                //根据回调参数中的out_trade_no 查询交易记录表 得到支付状态
                // select * from paymentInfo where out_trade_no=?
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(out_trade_no, PaymentType.ALIPAY.name());
                //判断支付状态 如果是PAID ClOSED则返回"failure
                //验证金额 验证out_trade_no app_id 全部通过才能返回success
                if(paymentInfo.getPaymentStatus() == PaymentStatus.PAID.name() || paymentInfo.getPaymentStatus() == PaymentStatus.ClOSED.name()){
                    //这次支付不是本订单的第一次支付    return "failure"; 反馈支付宝校验失败
                    return "failure";
                }
                // 正常的支付成功，我们应该更新交易记录状态
                paymentService.paySuccess(out_trade_no,PaymentType.ALIPAY.name(), paramMap);

                //反馈支付宝校验成功
                return "success";
            }

        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }

        return "failure";
    }


    // 发起退款！http://localhost:8205/api/payment/alipay/refund/20
    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable(value = "orderId")Long orderId) {
        // 调用退款接口
        boolean flag = alipayService.refund(orderId);

        return Result.ok(flag);
    }

    //checkPayment方法只查询支付宝中是否有交易记录 只要有交易记录就返回true  然后才能再去关闭交易 未付款关闭成功 已付款关闭失败
    // 只要用户扫描了二维码那么不管有没有付钱支付宝中都会有交易记录
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 调用查询接口
        boolean flag = alipayService.checkPayment(orderId);
        return flag;
    }

    //通过outTradeNo 第三方支付编号 去数据库查询是否有交易记录
    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (null!=paymentInfo){
            return paymentInfo;
        }
        return null;
    }

    //根据orderId 关闭支付宝中的交易记录
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        Boolean aBoolean = alipayService.closePay(orderId);

        return aBoolean;
    }


}