package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author ldh
 * @create 2020-04-18 14:16
 */

public interface BaseTrademarkService extends IService<BaseTrademark> {
    //IService 里定义了对单表的操作 我们自己的service接口继承就好了 实现类也要继承IService的实现类

    /**
     * Banner自定义分页查询品牌列表
     * @param pageParam
     * @return
     */
    IPage<BaseTrademark> selectPage(Page<BaseTrademark> pageParam);

}
