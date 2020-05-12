package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author ldh
 * @create 2020-05-04 16:26
 */

@Service
@Transactional
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;


    @Value("${ware.url}")
    private String WARE_URL;

    /**
     * 保存订单 下单 返回订单号（订单表id）
     *
     * @param orderInfo 前端通过vue异步传递orderInfo json串 已经有一部分数据了
     * @return
     */
    @Override
    public Long saveOrderInfo(OrderInfo orderInfo) {
        //保存orderInfo表 前端已经保存一部分  少了用户id 总金额 订单状态 第三方交易编号 创建时间 过期时间 进程状态 订单主题表述
        //为总金额赋值
        orderInfo.sumTotalAmount();

        //为订单状态赋值
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());//未支付

        //第三方支付编号，给支付宝使用的，为了保证交易的幂等性 每个订单是唯一的
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);

        //赋值创建时间
        orderInfo.setCreateTime(new Date());

        //赋值过期时间 创建时间加一天 先获取日历对象
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DATE,1);
        orderInfo.setExpireTime(instance.getTime());

        //进程状态 与订单状态有个绑定关系
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        // 订单的主题描述：获取订单明细中的商品名称，将商品名称拼接在一起。 先获取订单明细表
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuilder sb=new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            sb.append(orderDetail.getSkuName()+" ");
        }
        //判断是否超长
        if(sb.toString().length()>200){
            orderInfo.setTradeBody(sb.toString().substring(0,100));
        }else {
            orderInfo.setTradeBody(sb.toString());
        }
        //数据封装完毕 插入数据库
        orderInfoMapper.insert(orderInfo);

        //保存订单明细  先获取订单明细集合 每个OrderDetail对象中确少orderId
        for (OrderDetail orderDetail : orderDetailList) {
            //设置订单明细id为null 让其自增长
            orderDetail.setId(null);
            //为每个订单明细 orderDetail对象赋值orderId
            orderDetail.setOrderId(orderInfo.getId());
            //插入订单明细表
            orderDetailMapper.insert(orderDetail);
        }

        //mq发送延迟消息 到点未支付就取消订单  交换机 路由键 消息 延迟时间
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,
                MqConst.ROUTING_ORDER_CANCEL, orderInfo.getId(), MqConst.DELAY_TIME);

        //返回订单编号
        return orderInfo.getId();
    }

    /**
     * 根据用户id生成订单结算流水号 并且放入缓存（用于防止同一订单重复提交）
     * @param userId
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        //生成流水号
        String tradeNo = UUID.randomUUID().toString().replace("-", "");

        //将流水号放入缓存
        String tradeNoKey = "user:" + userId + ":tradeCode";
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo);
        return tradeNo;
    }

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        //定义缓存流水号key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        //获取缓存中的流水号
        String redisTradeNo  = (String) redisTemplate.opsForValue().get(tradeNoKey);

        return tradeCodeNo.equals(redisTradeNo);
    }

    /**
     * 删除缓存中的订单结算流水号
     * @param userId
     */
    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // 删除缓存中订单结算流水号数据
        redisTemplate.delete(tradeNoKey);
    }


    /**
     * 验证要购买商品的库存
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public Boolean checkStock(Long skuId, Integer skuNum) {
        //远程调用库存系统 使用HttpClientUtil
        // 远程调用http://localhost:9001/hasStock?skuId=10221&num=2 发送请求
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);

        return "1".equals(result);
    }

    /**
     * 处理过期订单 取消订单
     * @param orderId
     */
    @Override
    public void execExpiredOrder(Long orderId,String flag) {
        //调用修改订单状态方法 取消订单也就是修改订单状态为close
        updateOrderStatus(orderId, ProcessStatus.CLOSED);

        if("2".equals(flag)){
            //修改交易记录表为close
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
        }
    }

    /**
     * 根据订单Id 修改订单的状态 供处理过期订单 取消订单调用
     * @param orderId
     * @param processStatus  要修改为的订单状态改成processStatus
     */
    @Override
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        //创建修改订单的订单的信息
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setOrderStatus(processStatus.name());

        //修改订单信息 取消该订单
        orderInfoMapper.updateById(orderInfo);
    }

    /**
     * 根据订单Id 查询订单详情信息带着订单明细信息 供支付模块调用
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        //不光要查询订单信息还要查询一个订单明细信息
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);

        //获取订单明细集合
        QueryWrapper<OrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id",orderId);
        List<OrderDetail> orderDetails = orderDetailMapper.selectList(wrapper);

        //为订单信息对象赋值订单详情集合
        orderInfo.setOrderDetailList(orderDetails);
        return orderInfo;
    }

    //支付成功后 发送消息减库存
    @Override
    public void sendOrderStatus(Long orderId) {
        //减库存 更新订单状态为已通知仓库
        updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);

        //发送库存的消息 是个json串 库存中已经有了消费方法
        String wareJson = initWareOrder(orderId);
        //像库存系统发送减库存发货消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, wareJson);
    }

    // 根据orderId 获取发送减库存需要的json 字符串
    public String initWareOrder(Long orderId) {
        //json字符串由orderinfo数据组成
        //根据订单id 获取orderinfo
        OrderInfo orderInfo = getOrderInfo(orderId);

        // 将orderInfo中部分数据转换为Map 减库存需要的数据
        Map map = initWareOrder(orderInfo);

        //将map转化为json返回
        return JSON.toJSONString(map);

    }

    @Override
    //  将orderInfo中部分数据转换为Map
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        //封装仓库减库存需要的参数  减库存 发货
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时orderInfo中才会有wareId值！

        /*
            map.put("details", mapArrayList);
            details:[{skuId:101,skuNum:1,skuName:’小米手64G’},{skuId:201,skuNum:1,skuName:’索尼耳机’}]
            List<map>数据结构
         */
        //封装商品明细集合  把每一个订单明细中OrderDetail中的属性转为map 再把每个map存放进mapArrayList中
        ArrayList<Map> mapArrayList = new ArrayList<>();
        //获得订单明细集合
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();

        //循环遍历 每一个OrderDetail 将其转化为map
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId", orderDetail.getSkuId());
            orderDetailMap.put("skuNum", orderDetail.getSkuNum());
            orderDetailMap.put("skuName", orderDetail.getSkuName());
            mapArrayList.add(orderDetailMap);
        }
        map.put("details", mapArrayList);
        return map;
    }


    //一个订单中的商品不在一个仓库中时需要拆单
    @Override
    public List<OrderInfo> orderSplit(long orderId, String wareSkuMap) {
       /*
        1.  先获取到原始订单 107
        2.  将wareSkuMap 转换为我们能操作的对象 [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
            方案一：class Param{
                        private String wareId;
                        private List<String> skuIds;
                    }
            方案二：看做一个Map mpa.put("wareId",value); map.put("skuIds",value)

        3.  遍历List<Map> maps 创建一个新的子订单 108 109 。。。
        4.  给子订单赋值
        5.  保存子订单到数据库
        6.  修改原始订单的状态
        7.  测试
     */
       //创建一个最终返回子订单集合
        List<OrderInfo> orderInfoArrayList = new ArrayList<>();

        //获取原始订单
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);

        //将wareSkuMap 转换为能操作的map集合  maps集合中的每一个map对都是一个子订单 map的value是该子订单的所有商品id
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);

        //判断maps是否为空
        if(maps != null && maps.size()>0){
            //遍历maps 集合将集合中的每一个map转化为一个子订单
            for (Map map : maps) {

                //创建子订单对象  然后从当前map中取属性 为其赋值
                OrderInfo subOrderInfo = new OrderInfo();

                //获取仓库id
                String wareId = (String) map.get("wareId");
                //获取该仓库下的skuid集合（该子订单的商品skuid集合）
                List<String> skuIds = (List<String>) map.get("skuIds");

                // 为自订单对象赋值
                BeanUtils.copyProperties(orderInfoOrigin, subOrderInfo);
                // 防止主键冲突 设置子订单id自增
                subOrderInfo.setId(null);
                //为子订单的父订单id赋值
                subOrderInfo.setParentOrderId(orderId);
                //为子订单的仓库Id赋值
                subOrderInfo.setWareId(wareId);


                //为子订单的订单明细集合赋值 先创建子订单的订单明细集合
                List<OrderDetail> orderDetails = new ArrayList<>();

                //获取原始订单的订单明细集合  子订单的订单明细都在这里面
                List<OrderDetail> orderDetailOriginList = orderInfoOrigin.getOrderDetailList();
                if(orderDetailOriginList !=null && orderDetailOriginList.size()>0){

                    //遍历原始订单明细集合 如果当前订单明细的skuid在skuIds中 则此订单明细属于当前子订单
                    for (OrderDetail orderDetail : orderDetailOriginList) {
                        for (String skuId : skuIds) {
                            //对于每一个orderDetail都让其与skuIds中的所有skuId比较
                            if(Long.parseLong(skuId) == orderDetail.getSkuId().longValue()){
                                // 将当前订单明细添加到子订单集合
                                orderDetails.add(orderDetail);
                            }
                        }

                    }
                }
                //为子订单的订单明细集合赋值
                subOrderInfo.setOrderDetailList(orderDetails);

                //计算的当前子订单的总金额
                subOrderInfo.sumTotalAmount();

                //保存子订单到数据库  订单明细也都保存了
                saveOrderInfo(subOrderInfo);

                // 将当前子订单添加到orderInfoArrayList集合中！
                orderInfoArrayList.add(subOrderInfo);

            }
        }

        // 修改原始订单的状态为已拆分
        updateOrderStatus(orderId, ProcessStatus.SPLIT);

        return orderInfoArrayList;
    }


}
