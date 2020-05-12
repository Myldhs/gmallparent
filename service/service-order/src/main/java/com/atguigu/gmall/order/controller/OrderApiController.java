package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-04 14:50
 */
@RestController
@RequestMapping("/api/order")
public class OrderApiController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;



    /**
     * 点击去结算后确认订单页面的数据   ....auth路径必须要登录  生成订单结算流水号
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request){

        // 使用工具类获取到用户Id
        String userId = AuthContextHolder.getUserId(request);

        //获取用户地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        
        //先得到用户想要购买的已选中的商品！ 订单明细中的数据是来自购物车的
        List<CartInfo> cartInfoList  = cartFeignClient.getCartCheckedList(userId);

        //将目标商品转化为订单明细
        // 声明一个集合来存储所有要购买商品的订单明细
        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();

        //定义商品总件数
        int totalNum=0;

        for (CartInfo cartInfo : cartInfoList) {
            //创建订单明细
            OrderDetail orderDetail = new OrderDetail();

            //向orderDetail中封装数据
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());

            //记录商品总件数
            totalNum = totalNum + cartInfo.getSkuNum().intValue();

            // 添加到集合
            detailArrayList.add(orderDetail);
        }

        //创建OrderInfo对象 封装订单信息
        OrderInfo orderInfo = new OrderInfo();
        //为OrderInfo中的订单明细集合赋值 一个订单有多个商品
        orderInfo.setOrderDetailList(detailArrayList);
        //计算订单总价格
        orderInfo.sumTotalAmount();

        //创建存放返回给前端数据的集合
        Map<String, Object> result = new HashMap<>();
        //将userAddressList 放入集合
        result.put("userAddressList", userAddressList);
        //将userAddressList 放入集合
        result.put("detailArrayList", detailArrayList);
        // 保存要购买商品总数量
        result.put("totalNum", totalNum);
        // 保存要购买商品总金额
        result.put("totalAmount", orderInfo.getTotalAmount());

        //保存生成一个订单流水号，返回给前端保存
        String tradeNo = orderService.getTradeNo(userId);
        result.put("tradeNo", tradeNo);
 
        //将封装进map中的数据返回  订单确认页面需要的数据
        return Result.ok(result);

    }

    /**
     * 提交订单
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        // 获取到用户Id (前台没有传)
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));

        //下订单之间校验流水号，不能无刷新重复提交 获取前端传进来的订单流水号
        String tradeNo = request.getParameter("tradeNo");
        // 调用服务层的比较方法
        boolean flag = orderService.checkTradeCode(userId, tradeNo);
        if (!flag) {
            // 比较失败！
            return Result.fail().message("不能重复提交订单！");
        }
        //比较之后删除缓存中的流水号
        orderService.deleteTradeNo(userId);

        //对每一个商品验证库存和价格 每个购买的商品都要验证 循环订单明细中的每个商品
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if(null!=orderDetailList && orderDetailList.size()>0){
            for (OrderDetail orderDetail : orderDetailList) {
                //判断当前商品库存是否充足
               Boolean result = orderService.checkStock(orderDetail.getSkuId(),orderDetail.getSkuNum());
               if(!result){
                   //当前商品没有足够的库存
                   return Result.fail().message(orderDetail.getSkuName() + "库存不足！");
               }

                //验证要购买的商品价格是否有变动（下单和付款时商品价格可能会改变）
                // 获取当前商品的实时价格
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if(orderDetail.getOrderPrice().compareTo(skuPrice)!=0){
                    //重新去数据库查询该用户的购物车商品信息，更新到缓存中 商品数据变化 购物车中的商品数据要更新
                    cartFeignClient.loadCartCache(userId);

                    return Result.fail().message(orderDetail.getSkuName() + "价格有变动！");
                }
            }
        }

        // 校验通过 保存订单！ 返回订单号
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return Result.ok(orderId);
    }

    /**
     * 供内部（支付模块）调用 根据订单id获取订单详情信息（带着订单详情） 展示在选择支付方式（付款）页面上
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId){
        //获取订单详情数据信息
        return orderService.getOrderInfo(orderId);
    }


    /**
     * 拆单业务 库存系统调用此方法 发送时携带参数 orderid 和 wareSkuMap 仓库与商品对照表
     * @param request
     * @return
     */
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        //获取传递过来的原始订单id参数
        String orderId = request.getParameter("orderId");

        //获取传递过来的 仓库与购买商品的sku map式的对应关系
        // [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        String wareSkuMap = request.getParameter("wareSkuMap");

        //实行拆单 获取拆分的子订单集合
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId),wareSkuMap);

        //将每个子订单中的部分数据转为map 所有的子订单map都存放在mapArrayList中 每一个map就是一个子订单
        // 声明一个存储子订单中的字符串map的集合
        ArrayList<Map> mapArrayList = new ArrayList<>();

        // 遍历子订单集合  将子订单中的部分数据转为map 在mapArrayList中每一个map就是一个子订单
        for (OrderInfo orderInfo : subOrderInfoList) {
            //将orderInfo中部分数据转换为Map 带上他们的订单明细集合
            Map map = orderService.initWareOrder(orderInfo);
            // 将该订单的map添加到集合中！
            mapArrayList.add(map);
        }
        //返回所有子订单的map的list集合的json串
        return JSON.toJSONString(mapArrayList);
    }


    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo) {
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }



}
