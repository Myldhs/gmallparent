package com.atguigu.gmall.list.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.Goods;
import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * @author ldh
 * @create 2020-04-27 12:01
 */
@RestController
@RequestMapping("/api/list") //往es中存放sku的数据
public class ListApiController {

    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    @Autowired
    private SearchService searchService;


    //通过我们设定的Goods类 初始化es 通过Java代码创建es的index type 还是自定义mapping

    @GetMapping("inner/createIndex")
    public Result createIndex() {
        //创建index
        restTemplate.createIndex(Goods.class);

        //自定义创建映射
        restTemplate.putMapping(Goods.class);

        return Result.ok();
    }

    /**
     * 上架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/upperGoods/{skuId}")
    public Result upperGoods(@PathVariable("skuId") Long skuId) {
        searchService.upperGoods(skuId);
        return Result.ok();
    }


    /**
     * 下架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/lowerGoods/{skuId}")
    public Result lowerGoods(@PathVariable("skuId") Long skuId) {
        searchService.lowerGoods(skuId);
        return Result.ok();
    }

    /**
     * 根据商品的skuId 更新商品热点数据  暂存在redis中 每访问一次加一 达到 一定数值去es进行更新操作
     * @param skuId
     */
    @GetMapping("inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable("skuId") Long skuId) {
        // 调用服务层 更新热点数据
        searchService.incrHotScore(skuId);
        return Result.ok();
    }

    /**
     * 首页搜索商品 通过关键字 三级分类  品牌  平台属性筛选 使用json 传值，接收，将json转化为java 对象
     *
     * @param searchParam
     * @return
     * @throws IOException
     */
    @PostMapping
    public Result list(@RequestBody SearchParam searchParam) throws IOException {
        SearchResponseVo response = searchService.search(searchParam);
        return Result.ok(response);
    }




}
