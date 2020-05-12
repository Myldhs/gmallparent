package com.atguigu.gmall.list.Repository;

import com.atguigu.gmall.model.list.Goods;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author ldh
 * @create 2020-04-27 14:10
 */
//继承操作es的类 本类也可以操作es 可以进行es的一些简单操作
public interface GoodsRepository extends ElasticsearchRepository<Goods,Long> {

}
