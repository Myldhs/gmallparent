package com.atguigu.gmall.cart.client;

import com.atguigu.gmall.cart.client.impl.CartDegradeFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CartInfo;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;


/**
 * 购物车API接口
 * @author ldh
 * @create 2020-05-04 10:08
 */
@FeignClient(value = "service-cart", fallback = CartDegradeFeignClient.class)
public interface CartFeignClient {

    /**
     * 登录用户或者临时用户 添加商品到购物车
     * @param skuId
     * @param skuNum
     * @return
     */
    @ApiOperation("登录用户或者临时用户 添加商品到购物车")
    @PostMapping("/api/cart/addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable("skuId") Long skuId,
                            @PathVariable("skuNum") Integer skuNum);

    /**
     * 根据用户Id 查询购物车列表中被选中的商品
     * @param userId
     * @return
     */
    @GetMapping("/api/cart/getCartCheckedList/{userId}")
    List<CartInfo> getCartCheckedList(@PathVariable("userId") String userId);

    /**
     * 根据用户Id 查询数据库中的购物车数据，并添加到缓存！   根据用户Id查询该用户购物车中商品的最新数据并放入缓存
     * 只要是去数据库中获取购物车信息的 说明缓存中的数据都不可用了，所以获取后都要将数据存放到缓存中 进行覆盖更新
     * @param userId
     * @return
     */
    @GetMapping("/api/cart/loadCartCache/{userId}")
    Result loadCartCache(@PathVariable("userId") String userId);



}
