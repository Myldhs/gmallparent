package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-11 11:30
 */
@Controller
public class SeckilController {


    @Autowired
    private ActivityFeignClient activityFeignClient;

    /**
     * 展现所有秒杀列表页面
     * @param model
     * @return
     */
    @GetMapping("seckill.html")
    public String index(Model model) {
        Result result = activityFeignClient.findAll();
        model.addAttribute("list", result.getData());
        return "seckill/index";
    }

    /**
     * 根据skuId获取该秒杀商品的详情页
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        // 通过skuId 查询skuInfo
        Result result = activityFeignClient.getSeckillGoods(skuId);
        model.addAttribute("item", result.getData());
        return "seckill/item";
    }

    /**
     * 获取到下单吗之后进入秒杀排队 带着skuIdStr和skuId请求这里  排队页面需要skuId和skuIdStr数据
     * @param skuId
     * @param skuIdStr
     * @param request
     * @return
     */
    @GetMapping("seckill/queue.html")
    public String queue(@RequestParam(name = "skuId") Long skuId,
                        @RequestParam(name = "skuIdStr") String skuIdStr,
                        HttpServletRequest request){
        request.setAttribute("skuId", skuId);
        request.setAttribute("skuIdStr", skuIdStr);
        return "seckill/queue";
    }

    /**
     * 确认订单 抢到下单资格后的下单确认页面
     * @param model
     * @return
     */
    @GetMapping("seckill/trade.html")
    public String trade(Model model) {
        //获取秒杀下单数据
        Result<Map<String, Object>> result = activityFeignClient.trade();
        //判断
        if(result.isOk()) {
            //将数据保存给页面渲染  返回下单页面
            model.addAllAttributes(result.getData());
            return "seckill/trade";
        } else {
            //失败 渲染失败页面
            model.addAttribute("message",result.getMessage());

            return "seckill/fail";
        }
    }


}
