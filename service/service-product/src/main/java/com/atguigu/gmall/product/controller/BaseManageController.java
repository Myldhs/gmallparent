package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author ldh
 * @create 2020-04-17 17:31
 */
@Api(tags = "商品基础属性接口")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/admin/product")
//@CrossOrigin //跨域请求注解
public class BaseManageController {

    @Autowired
    private ManageService manageService;

    /**
     * 查询所有的一级分类信息
     * @return
     */
    @ApiOperation("查询所有的一级分类信息")
    @GetMapping("getCategory1")
    public Result<List<BaseCategory1>> getCategory1(){
        List<BaseCategory1> category1List = manageService.getCategory1();
        //Result 统一返回结果类 泛型是返回结果携带数据的类型 即Result类中data属性的类型
        return Result.ok(category1List);
    }


    /**
     * 根据一级分类Id 查询二级分类数据
     * @param category1Id
     * @return
     */
    @ApiOperation("根据一级分类Id 查询二级分类数据")
    @GetMapping("getCategory2/{category1Id}")
    public Result<List<BaseCategory2>> getCategory2(@PathVariable("category1Id") Long category1Id){
        List<BaseCategory2> category2List = manageService.getCategory2(category1Id);
        return Result.ok(category2List);
    }

    /**
     * 根据二级分类Id 查询三级分类数据
     * @param category2Id
     * @return
     */
    @ApiOperation("根据二级分类Id 查询三级分类数据")
    @GetMapping("getCategory3/{category2Id}")
    public Result<List<BaseCategory3>> getCategory3(@PathVariable("category2Id") Long category2Id){
        List<BaseCategory3> category3List = manageService.getCategory3(category2Id);
        return Result.ok(category3List);
    }

    /**
     * 根据所有分类Id 获取该分类下的平台属性数据
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     */
    @ApiOperation("根据所有分类Id 获取平台属性数据")
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result<List<BaseAttrInfo>> attrInfoList(@PathVariable("category1Id") Long category1Id,
                                                   @PathVariable("category2Id") Long category2Id,
                                                   @PathVariable("category3Id") Long category3Id){

        List<BaseAttrInfo> attrInfoList = manageService.getAttrInfoList(category1Id, category2Id, category3Id);
        return Result.ok(attrInfoList);
    }


    /**
     * 保存平台属性方法
     * @param baseAttrInfo
     * @return
     * 接收的数据应该是 BaseAttrInfo 中的每个属性组成的json 字符串
     *后台系统页面是vue 制作，vue 保存的时候，传递过来的是Json 字符串。
     *数据需要在后台用Java 对象接收 { json字符串 --@RequestBody--> JavaObject 【BaseAttrInfo】}
     *JavaObject---@ResponsBody--->Json 字符串
     */
    @ApiOperation("保存平台属性方法")
    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo  baseAttrInfo){
        manageService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }

    /**
     * 根据平台属性id attrId 查询平台属性值
     * @param attrId
     * @return
     */
    @ApiOperation("根据平台属性id attrId 查询平台属性对象")
    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList (@PathVariable("attrId") Long attrId){
        //查询平台属性对象
        BaseAttrInfo baseAttrInfo =  manageService.getAttrInfo(attrId);

        //获取平台属性值
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();

        return Result.ok(attrValueList);


    }

    /**
     * 根据三级分类id 分页查询该分类下的spu商品信息
     * @param page
     * @param limit
     * @return
     */
    @ApiOperation("根据三级分类id 分页查询该分类下的spu商品信息")
    @GetMapping("{page}/{limit}")
    public Result<IPage<SpuInfo>> index (
            @PathVariable("page") Long page ,
            @PathVariable("limit") Long limit, SpuInfo spuInfo){

        //创建分页条件
        Page<SpuInfo> infoPage = new Page<>(page, limit);

        //分页查询
        IPage<SpuInfo> infoIPage=  manageService.selectPage(infoPage,spuInfo);

        return Result.ok(infoIPage);

    }







}
