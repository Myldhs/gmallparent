package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.api.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author ldh
 * @create 2020-04-20 11:29
 */
@Api(tags = "Sku管理")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/admin/product")
public class SkuManagerController {

    @Autowired
    private ManageService manageService;

    /**
     * 根据spuId 查询spuImageList
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId 查询spuImageList")
    @GetMapping("spuImageList/{spuId}")
    public Result getSpuImageList(@PathVariable("spuId") Long spuId){

        List<SpuImage> spuImageList = manageService.getSpuImageList(spuId);

        return Result.ok(spuImageList);
    }

    /**
     * 根据spuId 查询该spu商品下的所有的销售属性
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId 查询该spu商品下的所有的销售属性及属性值")
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result getSpuSaleAttrList (@PathVariable("spuId") Long spuId){

       List<SpuSaleAttr> spuSaleAttrList =  manageService.getSpuSaleAttrList(spuId);

       return  Result.ok(spuSaleAttrList);

    }

    /**
     * 添加保存sku 将前台传来的数据保存在四张表中
     * @param skuInfo
     * @return
     */
    @ApiOperation("添加保存sku")
    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo){
        //添加保存sku
        manageService.saveSkuInfo(skuInfo);

        return Result.ok();
    }

    /**
     * 分页查询所有sku列表
     * @param page
     *  @param limit
     * @return
     */
    @ApiOperation("分页查询所有sku列表")
    @GetMapping("list/{page}/{limit}")
    public Result getSkuInfoList(@PathVariable("page") Long page ,
                                 @PathVariable("limit") Long limit){
        //创建分页查询条件
        Page<SkuInfo> skuInfoPage = new Page<>(page, limit);

        //进行分页查询
        IPage<SkuInfo> skuInfoIPage = manageService.selectSkuInfoList(skuInfoPage);

        return Result.ok(skuInfoIPage);
    }

    /**
     * 根据skuId 上架sku  本质就是更改sku_info表is_sale状态 0下架  1上架
     * @param skuId
     * @return
     */
    @ApiOperation("根据skuId 上架sku")
    @GetMapping("onSale/{skuId}")
    public Result onSale (@PathVariable("skuId") Long skuId){
        manageService.onSale(skuId);
        return Result.ok();
    }

    /**
     * 根据skuId 下架sku  本质就是更改sku_info表is_sale状态 0下架  1上架
     * @param skuId
     * @return
     */
    @ApiOperation("根据skuId 上架sku")
    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale (@PathVariable("skuId") Long skuId){
        manageService.cancelSale(skuId);
        return Result.ok();
    }




}
