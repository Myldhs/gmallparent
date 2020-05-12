package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ldh
 * @create 2020-05-06 16:01
 */
@Controller
public class PaymentController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    /**
     * 调用service-order模块获取订单详情信息 渲染支付页面
     * @param request
     * @return
     */
    @RequestMapping("pay.html")
    public String success(HttpServletRequest request, Model model){
        //获取前端传过来的详单id参数
        String orderId = request.getParameter("orderId");
        //获取订单详情信息
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(Long.parseLong(orderId));

        //存放进域中
        model.addAttribute("orderInfo",orderInfo);
        return "payment/pay";

    }

    /**
     * 重定向的目的地-支付成功页
     * @return
     */
    @GetMapping("pay/success.html")
    public String success() {
        return "payment/success";
    }


}
