package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.mapper.BaseTrademarkMapper;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author ldh
 * @create 2020-04-18 14:18
 */
@Service
public class BaseTrademarkServiceImpl extends ServiceImpl<BaseTrademarkMapper,BaseTrademark> implements BaseTrademarkService {


    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;
    /**
     * Banner自定义分页查询品牌列表
     * @param pageParam
     * @return
     */
    @Override
    public IPage<BaseTrademark> selectPage(Page<BaseTrademark> pageParam) {
        //创建查询条件对象
        QueryWrapper<BaseTrademark> wrapper = new QueryWrapper<>(null);
        wrapper.orderByDesc("id");

        //返回分页查询结果
        return baseTrademarkMapper.selectPage(pageParam,wrapper);
    }
}
