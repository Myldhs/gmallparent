package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ldh
 * @create 2020-05-04 10:27
 */
@Controller
public class CartController {

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    /**
     * 添加购物车  请求通过feign调用之前（由web-all到feign）会是丢失请求头文件，所以在feign调用之前在拦截器中为请求设置包含用户id的头信息
     * @param skuId
     * @param skuNum
     * @param request
     * @return
     */
    @RequestMapping("addCart.html")
    public String addCart(@RequestParam(name = "skuId") Long skuId,
                          @RequestParam(name = "skuNum") Integer skuNum,
                          HttpServletRequest request){
        //远程调用添加购物车
        cartFeignClient.addToCart(skuId,skuNum);

        //查询添加到购物车的sku信息给前端展示
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        request.setAttribute("skuInfo",skuInfo);
        request.setAttribute("skuNum",skuNum);

        return "cart/addCart";

    }

    /**
     * 查看购物车列表  购物车数据不从web-all获取，而是在页面用vue异步调用service-cart模块获取购物车数据
     * @param request
     * @return
     */
    @RequestMapping("cart.html")
    public String index(HttpServletRequest request){

        return "cart/index";
    }







}
