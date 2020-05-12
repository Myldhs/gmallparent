package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author ldh
 * @create 2020-04-18 14:30
 */
@Api(tags = "商品品牌管理")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/admin/product/baseTrademark")
public class BaseTrademarkController {

    @Autowired
    private BaseTrademarkService trademarkService;


    /**
     * Banner自定义分页查询品牌列表
     * @param page
     * @param limit
     * @return
     */
    @ApiOperation(value = "自定义分页查询品牌列表")
    @GetMapping("/{page}/{limit}")
    public Result index(@PathVariable("page")Long page,
                        @PathVariable("limit")Long limit){
        //创建分页查询对象
        Page<BaseTrademark> trademarkPage = new Page<>(page, limit);

        //进行分页查询
        IPage<BaseTrademark> baseTrademarkIPage = trademarkService.selectPage(trademarkPage);

        //返回分页查询数据
        return Result.ok(baseTrademarkIPage);
    }

    //查询所有品牌  用于添加spu时 选择商品的品牌
    @ApiOperation(value = "查询所有品牌")
    @GetMapping("getTrademarkList")
    public Result getTrademarkList(){
        List<BaseTrademark> list = trademarkService.list(null);

        //返回品牌查询数据
        return Result.ok(list);
    }


    //删除品牌
    @ApiOperation(value = "删除品牌")
    @DeleteMapping("/remove/{id}")
    public Result remove(@PathVariable("id")Long id){
        trademarkService.removeById(id);
        return Result.ok();
    }

    //根据品牌id 来获取品牌
    @ApiOperation(value = "根据品牌id 来获取品牌")
    @GetMapping("get/{id}")
    public Result get(@PathVariable("id")Long id){
        //获取数据
        BaseTrademark baseTrademark = trademarkService.getById(id);
        return Result.ok(baseTrademark);
    }

    //保存品牌信息
    @ApiOperation(value = "保存品牌信息")
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){
        //保存数据
        trademarkService.save(baseTrademark);
        return Result.ok();
    }

    //更新品牌信息
    @ApiOperation(value = "更新品牌信息")
    @PutMapping("update")
    public Result update(@RequestBody BaseTrademark baseTrademark){
        //修改数据
        trademarkService.updateById(baseTrademark);
        return Result.ok();
    }


}
