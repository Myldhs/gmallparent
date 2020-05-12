package com.atguigu.gmall.product.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 这个类中的所有数据是为了供外部调用给外部提供数据使用的！
 * @author ldh
 * @create 2020-04-21 20:10
 */
@Api(tags = "为前台模块个平台访问提供商品数据 只供内部调用访问")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/api/product")
public class ProductApiController {

    @Autowired
    private ManageService manageService;

    /**
     *为前台模块提供商品数据 只供内部调用访问
     * 根据skuId获取sku基本信息
     * @param skuId
     * @return
     */
    //inner/*/* 这种控制器中的映射表示是内部接口调用。
    @ApiOperation("根据skuId获取sku基本信息(包含图片信息)")
    @GetMapping("inner/getSkuInfo/{skuId}")
    public SkuInfo getSkuInfo(@PathVariable("skuId") Long skuId){
        SkuInfo skuInfo =  manageService.getSkuInfo(skuId);
        return skuInfo;
    }

    /**
     * 通过三级分类id查询分类信息
     * @param category3Id
     * @return
     */
    @ApiOperation("通过三级分类id查询分类信息")
    @GetMapping("inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryView(@PathVariable("category3Id") Long category3Id){
        BaseCategoryView baseCategoryView =  manageService.getCategoryViewByCategory3Id(category3Id);
        return baseCategoryView;
    }

    /**
     * 跟据前端传过来的skuId单独获取sku价格信息
     * @param skuId
     * @return
     */
    @ApiOperation("跟据前端传过来的skuId单独获取sku价格信息")
    @GetMapping("inner/getSkuPrice/{skuId}")
    public BigDecimal getSkuPrice(@PathVariable("skuId") Long skuId){

       return manageService.getSkuPriceBySkuId(skuId);

    }

    /**
     * 根据spuId，skuId 查询该spu所有的销售属性及销售属性值，并根据skuId高亮显示该sku的销售属性值（即选中状态）
     * @param skuId
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId，skuId 查询销售属性集合")
    @GetMapping("inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(
                                                    @PathVariable Long spuId,
                                                    @PathVariable Long skuId){

        return manageService.getSpuSaleAttrListCheckBySku(spuId,skuId);
    }

    /**
     * 根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId 查询该spu下所有的sku的销售属性值")
    @GetMapping("inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable("spuId") Long spuId){
        return manageService.getSkuValueIdsMap(spuId);
    }


    /**
     * 被web-all远程获取全部分类信息 没有inner 因为首页的分类数据是可以任意看的不用登录
     * @return
     */
    @ApiOperation("被web-all远程获取全部分类信息")
    @GetMapping("getBaseCategoryList")
    public Result getBaseCategoryList() {
        List<JSONObject> list = manageService.getBaseCategoryList();
        return Result.ok(list);
    }

    /**
     * 通过skuinfo中的品牌Id 来查询该sku的品牌数据  用于es封装数据
     * @param tmId
     * @return
     */
    @GetMapping("inner/getTrademark/{tmId}")
    public BaseTrademark getTrademark(@PathVariable("tmId")Long tmId){
        return manageService.getTrademarkByTmId(tmId);
    }

    /**
     * 通过商品的skuId 来查询该sku的平台属性数据（属性名属性值 关联多个表）
     * @param skuId
     * @return
     * 最终结果得到的是 指定skuId的平台属性集合 集合中是平台属性对象 每个平台属性对象中国有该sku的该平台属性的属性值
     */
    @GetMapping("inner/getAttrList/{skuId}")
    public List<BaseAttrInfo> getAttrList(@PathVariable("skuId") Long skuId){
        return manageService.getAttrList(skuId);
    }



}
