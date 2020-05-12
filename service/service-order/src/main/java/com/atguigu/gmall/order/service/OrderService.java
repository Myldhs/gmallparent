package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-05-04 16:25
 */
public interface OrderService extends IService<OrderInfo> {

    /**
     * 保存订单 下单 返回订单号（订单表id）
     *
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    /**
     * 生成订单结算流水号 并且放入缓存（用于防止同一订单重复提交）
     * @param userId
     * @return
     */
    String getTradeNo(String userId);

    /**
     * 比较订单结算流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    boolean checkTradeCode(String userId, String tradeCodeNo);

    /**
     * 删除缓存中的订单结算流水号
     * @param userId
     */
    void deleteTradeNo(String userId);


    Boolean checkStock(Long skuId, Integer skuNum);


    /**
     * 处理过期订单 取消订单
     * @param orderId
     */
    void execExpiredOrder(Long orderId ,String flag);

    /**
     * 根据订单Id 修改订单的状态 供处理过期订单 取消订单调用
     * @param orderId
     * @param processStatus  要修改为的订单状态改成processStatus
     */
    void updateOrderStatus(Long orderId, ProcessStatus processStatus);

    /**
     * 根据订单Id 查询订单信息 供支付模块调用
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);


    void sendOrderStatus(Long orderId);

    //  将orderInfo中部分数据转换为Map
    Map initWareOrder(OrderInfo orderInfo);

    List<OrderInfo> orderSplit(long parseLong, String wareSkuMap);
}
