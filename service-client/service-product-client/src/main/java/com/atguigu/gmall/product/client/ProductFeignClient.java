package com.atguigu.gmall.product.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.impl.ProductDegradeFeignClient;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-04-22 9:28
 */
@FeignClient(value ="service-product" , fallback = ProductDegradeFeignClient.class)
public interface ProductFeignClient {

    /**
     *为前台模块提供商品数据 只供内部调用访问
     * 根据skuId获取sku基本信息
     * @param skuId
     * @return
     */
    //inner/*/* 这种控制器中的映射表示是内部接口调用。
    @ApiOperation("根据skuId获取sku基本信息(包含图片信息)")
    @GetMapping("/api/product/inner/getSkuInfo/{skuId}")
    public SkuInfo getSkuInfo(@PathVariable("skuId") Long skuId);

    /**
     * 通过三级分类id查询分类信息
     * @param category3Id
     * @return
     */
    @ApiOperation("通过三级分类id查询分类信息")
    @GetMapping("/api/product/inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryView(@PathVariable("category3Id") Long category3Id);

    /**
     * 跟据前端传过来的skuId单独获取sku价格信息
     * @param skuId
     * @return
     */
    @ApiOperation("跟据前端传过来的skuId单独获取sku价格信息")
    @GetMapping("/api/product/inner/getSkuPrice/{skuId}")
    public BigDecimal getSkuPrice(@PathVariable("skuId") Long skuId);

    /**
     * 根据spuId，skuId 查询该spu所有的销售属性及销售属性值，并根据skuId高亮显示该sku的销售属性值（即选中状态）
     * @param skuId
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId，skuId 查询销售属性集合")
    @GetMapping("/api/product/inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(
            @PathVariable("spuId") Long spuId,
            @PathVariable("skuId") Long skuId);

    /**
     * 根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId 查询该spu下所有的sku的销售属性值")
    @GetMapping("/api/product/inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable("spuId") Long spuId);

    /**
     * 被web-all远程获取全部分类信息 没有inner 因为首页的分类数据是可以任意看的不用登录
     * @return
     */
    @GetMapping("/api/product/getBaseCategoryList")
    Result getBaseCategoryList();

    /**
     * 通过skuinfo中的品牌Id 来查询该sku的品牌数据  用于es封装数据
     * @param tmId
     * @return
     */
    @GetMapping("/api/product/inner/getTrademark/{tmId}")
    BaseTrademark getTrademark(@PathVariable("tmId")Long tmId);


    /**
     * 通过商品的skuId 来查询该sku的平台属性数据（属性名属性值 关联多个表）
     * @param skuId
     * @return
     * 最终结果得到的是 指定skuId的平台属性集合 集合中是平台属性对象 每个平台属性对象中国有该sku的该平台属性的属性值
     */
    @GetMapping("/api/product/inner/getAttrList/{skuId}")
    List<BaseAttrInfo> getAttrList(@PathVariable("skuId") Long skuId);





}
