package com.atguigu.gmall.list.service;

import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;

import java.io.IOException;

/**
 * @author ldh
 * @create 2020-04-27 14:04
 */
public interface SearchService {

    /**
     * 根据商品的skuId上架商品列表
     * @param skuId
     */
    void upperGoods(Long skuId);

    /**
     * 根据商品的skuId下架商品列表
     * @param skuId
     */
    void lowerGoods(Long skuId);

    /**
     * 根据商品的skuId 更新商品热点数据
     * @param skuId
     */
    void incrHotScore(Long skuId);


    /**
     * 使用es进行搜索搜索列表
     * @param searchParam
     * @return
     * @throws IOException
     */
    SearchResponseVo search(SearchParam searchParam) throws IOException;



}
