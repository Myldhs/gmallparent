package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author ldh
 * @create 2020-04-21 19:56
 */
@Service
public class ItemServiceImpl implements ItemService {
    // 商品详情页面要想获取到数据，那么必须有一个skuId !
    // 那么skuId 在哪？如何传递到商品详情页面的？
    // 商品详情页面是从list.html 检索页面过来的。
    // https://item.jd.com/100005207363.html
    // https://item.jd.com 域名
    // 100005207363.html 控制器，并不是一个单纯的html
    // {skuId}.html

    @Autowired
    private ProductFeignClient productFeignClient;

    // 从spring 容器获取线程池
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    //调用List模块 更新sku热点数据
    @Autowired
    private ListFeignClient listFeignClient;


    /**
     * 我需要将数据封装到map中。
     * map.put("price","商品的价格")
     * map.put("skuInfo","skuInfo数据")
     * 根据skuId 调用service-product模块，获取sku详情信息
     * @param skuId
     * @return
     */
    @Override
    public Map<String, Object> getBySkuId(Long skuId) {

        //创建数据返回map
        HashMap<String, Object> resultMap = new HashMap<>();

        //创建异步对象先异步通过skuId获得 skuinfo基本数据
        CompletableFuture<SkuInfo> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //skuId可以是上面传进来的
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            // 保存skuInfo
            resultMap.put("skuInfo", skuInfo);

            //异步线程执行结果返回
            return skuInfo;
        }, threadPoolExecutor);

        //由skuCompletableFuture创建并行异步线程执行需要用到skuInfo的方法
        //通过三级分类id获取该sku分类数据
        CompletableFuture<Void> categoryViewCompletableFuture = skuCompletableFuture.thenAcceptAsync((skuInfo) -> {
            //通过三级分类id获取该sku分类数据
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());

            //保存sku商品分类数据
            resultMap.put("categoryView", categoryView);

        }, threadPoolExecutor);

        //单独获取sku价格 这个线程链就这一个节点就够了，所以不用有返回值 选择使用 runAsync
        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
            //单独获取sku价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            // 保存单独获取价格数据
            resultMap.put("price", skuPrice);

        }, threadPoolExecutor);

        //通过spuId 和skuId获取销售属性和属性值   销售属性-销售属性值回显并锁定
        CompletableFuture<Void> spuSaleAttrCompletableFuture = skuCompletableFuture.thenAcceptAsync((skuInfo) -> {
            //通过spuId 和skuId获取销售属性和属性值   销售属性-销售属性值回显并锁定
            List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getSpuId(), skuInfo.getId());

            // 保存通过spuId 和skuId获取销售属性和属性值
            resultMap.put("spuSaleAttrList", spuSaleAttrList);

        }, threadPoolExecutor);

        //根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
        CompletableFuture<Void> valuesSkuJsonCompletableFuture = skuCompletableFuture.thenAcceptAsync((skuInfo) -> {
            //根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());

            // 将根据spuId 查询该spu下所有的sku的销售属性值map转为json字符串
            String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
            // 保存valuesSkuJson
            resultMap.put("valuesSkuJson", valuesSkuJson);

        }, threadPoolExecutor);

        //更新该sku的热点数据
        CompletableFuture<Void> incrHotScoreCompletableFuture  = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);


        //上述4个并行异步线程全部执行完毕后 进行结果返回 等待所有任务完成才能继续往下走
        CompletableFuture.allOf(skuCompletableFuture,categoryViewCompletableFuture
                ,skuPriceCompletableFuture,spuSaleAttrCompletableFuture
                , valuesSkuJsonCompletableFuture,incrHotScoreCompletableFuture).join();

        //返回首页查询结果集
        return resultMap;

       /* //创建数据返回map
        HashMap<String, Object> resultMap = new HashMap<>();

        //通过skuId获得 skuinfo基本数据
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);

        //通过三级分类id获取该sku分类数据
        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());

        //单独获取sku价格
        BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);

        //通过spuId 和skuId获取销售属性和属性值   销售属性-销售属性值回显并锁定
        List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getSpuId(), skuInfo.getId());

        //根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
        Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());

        //封装数据到map中
        // 保存skuInfo
        resultMap.put("skuInfo",skuInfo);

        //保存sku商品分类数据
        resultMap.put("categoryView",categoryView);

        // 保存单独获取价格数据
        resultMap.put("price",skuPrice);

        // 保存通过spuId 和skuId获取销售属性和属性值
        resultMap.put("spuSaleAttrList",spuSaleAttrList);

        // 将根据spuId 查询该spu下所有的sku的销售属性值map转为json字符串
        String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
        // 保存valuesSkuJson
        resultMap.put("valuesSkuJson",valuesSkuJson);

        //返回首页查询结果集
        return resultMap;*/

    }
}
