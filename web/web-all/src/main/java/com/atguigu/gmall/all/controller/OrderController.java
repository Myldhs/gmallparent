package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-04 15:39
 */
@Controller
public class OrderController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    /**
     * 确认订单
     * @param model
     * @return
     */
    @GetMapping("trade.html")
    public String trade(Model model) {

        //获取确认订单页面需要的数据
        Result<Map<String, Object>> result = orderFeignClient.trade();

        //将数据存放进request域中
        model.addAllAttributes(result.getData());
        //渲染页面
        return "order/trade";
    }

}
