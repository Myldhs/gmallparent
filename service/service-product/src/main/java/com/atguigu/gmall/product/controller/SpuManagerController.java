package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseSaleAttr;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author ldh
 * @create 2020-04-18 16:19
 */
@Api(tags = "spu管理")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/admin/product")
public class SpuManagerController {

    @Autowired
    private ManageService manageService;

    // 添加spu页面 加载全部销售属性 请求URL http://api.gmall.com/admin/product/baseSaleAttrList
    @ApiOperation("添加spu页面加载销售属性")
    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){

        List<BaseSaleAttr> baseSaleAttrList =   manageService.getBaseSaleAttrList();

        return Result.ok(baseSaleAttrList);
    }

    /**
     * 保存spu 商品信息
     * @param spuInfo
     * @return
     * 添加spu 请求URL http://api.gmall.com/admin/product/saveSpuInfo
     */
    @ApiOperation("添加spu")
    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        // 调用服务层的保存方法
        manageService.saveSpuInfo(spuInfo);

        return Result.ok();
    }


}
