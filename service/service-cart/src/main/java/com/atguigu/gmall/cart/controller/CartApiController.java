package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author ldh
 * @create 2020-04-30 17:03
 */
@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    @Autowired
    private CartService cartService;

    /**
     * 登录用户或者临时用户 添加商品到购物车
     * @param skuId
     * @param skuNum
     * @param request
     * @return
     */
    @ApiOperation("登录用户或者临时用户 添加商品到购物车")
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable("skuId") Long skuId,
                            @PathVariable("skuNum") Integer skuNum,
                            HttpServletRequest request) {
        //获取登录用户id
        String userId = AuthContextHolder.getUserId(request);
        if(StringUtils.isEmpty(userId)){
            //用户未登录 获取临时用户id
            userId = AuthContextHolder.getUserTempId(request);
        }

        //添加商品到数据库购物车
        cartService.addToCart(skuId,userId,skuNum);
        return Result.ok();
    }

    /**
     * 根据用户id查询改用户的完整购物车信息
     * @param request
     * @return
     */
    @ApiOperation("根据用户id查询改用户的完整购物车信息")
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request){
        //获取登录用户id
        String userId = AuthContextHolder.getUserId(request);

        //获取临时用户id
        String userTempId = AuthContextHolder.getUserTempId(request);

        //查询用户的购物车信息
        List<CartInfo> cartList = cartService.getCartList(userId, userTempId);

        //返回购物车信息
        return Result.ok(cartList);
    }

    /**
     * 更新选中状态
     *
     * @param skuId
     * @param isChecked
     * @param request
     * @return
     */
    @ApiOperation("更新选中状态")
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable(value = "skuId") Long skuId,
                            @PathVariable(value = "isChecked") Integer isChecked,
                            HttpServletRequest request) {
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // update cartInfo set isChecked=? where  skuId = ? and userId=？
        if (StringUtils.isEmpty(userId)) {
            // 未登录获取临时用户id
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用更新方法
        cartService.checkCart(userId, isChecked, skuId);
        return Result.ok();
    }

    /**
     * 根据skuid删除购物车中的某项商品
     *
     * @param skuId
     * @param request
     * @return
     */
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable("skuId") Long skuId,
                             HttpServletRequest request) {
        // 获取userId
        String userId = AuthContextHolder.getUserId(request);
        //如果用户未登录
        if (StringUtils.isEmpty(userId)) {
            // 获取临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.deleteCart(skuId, userId);
        return Result.ok();
    }

    /**
     * 根据用户Id 查询购物车列表中被选中的商品
     * @param userId
     * @return
     */
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable(value = "userId") String userId) {
        return cartService.getCartCheckedList(userId);
    }

    /**
     * 根据用户Id 查询数据库中的购物车数据，并添加到缓存！   根据用户Id查询该用户购物车中商品的最新数据并放入缓存
     * 只要是去数据库中获取购物车信息的 说明缓存中的数据都不可用了，所以获取后都要将数据存放到缓存中 进行覆盖更新
     * @param userId
     * @return
     */
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId) {
        cartService.loadCartCache(userId);
        return Result.ok();
    }



}
