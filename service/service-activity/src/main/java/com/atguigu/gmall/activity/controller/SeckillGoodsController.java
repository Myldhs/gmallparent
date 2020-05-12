package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author ldh
 * @create 2020-05-11 11:26
 */
@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsController {

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private OrderFeignClient orderFeignClient;




    /**
     * 返回全部秒杀商品列表  去redis中获取
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        return Result.ok(seckillGoodsService.findAll());
    }

    /**
     * 根据skuID去redis中获取实体 获取秒杀商品详情
     *
     * @return
     */
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable("skuId") Long skuId) {
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    /**
     * 用户真正下单前，生成下单码 返回下单码 页面接收后将下单吗和商品id带上再请求后台 进行秒杀排队
     * 有，只在该商品秒杀时间范围内才能获取到下单码
     * skuIdStr 用户下单码 用户想要秒杀商品必须要有下单码
     * @param skuId
     * @return
     */
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        //获取用户登录id
        String userId = AuthContextHolder.getUserId(request);
        //获取用户要秒杀的商品
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        //判断秒杀商品是否存在
        if (null != seckillGoods) {
            Date curTime = new Date();
            //判断当前时间是否在该秒杀商品的秒杀时间内
            if (DateUtil.dateCompare(seckillGoods.getStartTime(), curTime) && DateUtil.dateCompare(curTime, seckillGoods.getEndTime())) {
                //在时间内 生成下单码 可以动态生成，放在redis缓存
                String skuIdStr = MD5.encrypt(userId);
                //将下单码返回
                return Result.ok(skuIdStr);
            }
        }
        return Result.fail().message("获取下单码失败");
    }


    /**
     * 进入排队页面后 页面异步请求这个控制器校验用户的秒杀资格
     * 判断当前用户有没有资格参与秒杀及要秒杀的商品还能不能被秒
     * 通过后放入mq秒杀队列中 进入秒杀队列的用户高并发情况下也有可能买不到（进入队列中的人数比秒杀商品的数量多）
     * @param skuId
     * @return
     */
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable("skuId") Long skuId, HttpServletRequest request) throws Exception {
        //获取用户的下单码（抢购码规则可以自定义）用户校验
        String userId = AuthContextHolder.getUserId(request);
        //获取页面传过来的下单码
        String skuIdStr = request.getParameter("skuIdStr");
        if (!skuIdStr.equals(MD5.encrypt(userId))) {
            //请求不合法  返回状态码
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }

        //用户有下单码后 校验秒杀商品的状态位  1：可以秒杀 0：秒杀结束 null 非法 获取保存在CacheHelper内存中的map 该商品的状态位
        String state = (String) CacheHelper.get(skuId.toString());
        if(StringUtils.isEmpty(state)){
            //请求不合法  返回状态码
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        if("1".equals(state)){
            //有下单码 商品可以秒杀 则创建用户秒杀实体类保存用户秒杀信息
            UserRecode userRecode = new UserRecode();
            userRecode.setUserId(userId);
            userRecode.setSkuId(skuId);

            //放进mq抢购队列中 进行抢购排队
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER, MqConst.ROUTING_SECKILL_USER, userRecode);
        }
        if("0".equals(state)){
            //已售罄 秒杀结束 返回状态码 213
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }

        return Result.ok();
    }

    /**
     * 根据skuid 和用户id前台页面轮询 查询秒杀状态
     * @return
     */
    @GetMapping(value = "auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        //当前登录用户
        String userId = AuthContextHolder.getUserId(request);
        //返回秒杀状态码
        return seckillGoodsService.checkOrder(skuId, userId);
    }


    /**
     * 秒杀确认订单 抢到下单资格后的下单确认页面数据
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request) {
        //显示收货人 送货清单 总金额等
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);

        // 根据用户id得到用户想要购买的秒杀商品！
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if (null == orderRecode) {
            return Result.fail().message("非法操作");
        }
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();

        //获取用户地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        // 声明一个集合来保存秒杀商品订单明细
        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();
        //封装秒杀商品订单明细对象
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        //秒杀数量从orderRecode中获取
        orderDetail.setSkuNum(orderRecode.getNum());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        // 添加到集合
        detailArrayList.add(orderDetail);

        // 秒杀订单总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        orderInfo.sumTotalAmount();

        //创建map保存页面需要的数据
        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", detailArrayList);
        // 保存总金额
        result.put("totalAmount", orderInfo.getTotalAmount());
        result.put("totalNum", orderRecode.getNum());

        return Result.ok(result);
    }

    /**
     * 秒杀提交订单
     *
     * @param orderInfo
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        //先获取用户id
        String userId = AuthContextHolder.getUserId(request);
        //为orderInfo中的userId赋值
        orderInfo.setUserId(Long.parseLong(userId));

        //获取用户秒杀商品信息
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if (null == orderRecode) {
            return Result.fail().message("非法操作");
        }

        //调用订单模块 保存秒杀订单
        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (null == orderId) {
            return Result.fail().message("下单失败，请重新操作");
        }

        //下单成功后 删除抢单信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);

        //缓存中保存用户下单记录 秒杀订单已经生成了
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId, orderId.toString());

        return Result.ok(orderId);
    }

}