package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

/**
 * @author ldh
 * @create 2020-04-30 11:20
 */
public interface CartService{
    // 添加购物车 用户Id，商品Id，商品数量。
    void addToCart(Long skuId, String userId, Integer skuNum);

    /**
     * 通过用户Id 查询该用户的购物车列表 用户可能是登录用户 也可能是临时用户
     * @param userId
     * @param userTempId
     * @return
     */
    List<CartInfo> getCartList(String userId, String userTempId);

    /**
     * 更新选中状态 那个用户（登录或未登录）的哪个商品的选中状态
     *
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId, Integer isChecked, Long skuId);

    /**
     * 根据skuid删除购物车中的某项商品
     * @param userId
     * @param skuId
     * @return
     */
    void deleteCart(Long skuId, String userId);

    /**
     * 根据用户Id 查询购物车列表中被选中的商品
     *
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);

    /**
     * 根据用户Id查询该用户购物车中商品的最新数据并放入缓存
     * @param userId
     * @return
     */
    List<CartInfo> loadCartCache(String userId);



}
