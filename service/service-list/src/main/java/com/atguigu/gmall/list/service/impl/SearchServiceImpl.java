package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.Repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ldh
 * @create 2020-04-27 14:05
 */
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private ProductFeignClient productFeignClient;

    //引入操作es的类
    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    //引入操作es的工具类 用于构建dsl语句等复杂操作
    @Autowired
    private RestHighLevelClient restHighLevelClient;



    /**
     * 根据商品的skuId上架商品列表
     * @param skuId
     */
    @Override
    public void upperGoods(Long skuId) {
        //上架 MySQL--》es 通过feign远程调用获取 获取各种需要的数据 封装为一个goods
        //创建一个Goods
        Goods goods = new Goods();

        //给goods赋值 通过productFeignClient获取信息
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if(null!=skuInfo){
            goods.setId(skuInfo.getId());
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setTitle(skuInfo.getSkuName());
            goods.setPrice(skuInfo.getPrice().doubleValue()); //bigdice 转为Double
            goods.setCreateTime(new Date());
        }

        //查询品牌数据
        BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
        if(null!=trademark){
            goods.setTmId(trademark.getId());
            goods.setTmName(trademark.getTmName());
            goods.setTmLogoUrl(trademark.getLogoUrl());
        }

        //获取分类数据
        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
        if(null!=categoryView){
            goods.setCategory1Id(categoryView.getCategory1Id());
            goods.setCategory1Name(categoryView.getCategory1Name());
            goods.setCategory2Id(categoryView.getCategory2Id());
            goods.setCategory2Name(categoryView.getCategory2Name());
            goods.setCategory3Id(categoryView.getCategory3Id());
            goods.setCategory3Name(categoryView.getCategory3Name());

        }

        //商品热度不需要 有默认值

        //给平台属性赋值 先通过远程调用productFeignClient获取平台属性数据 每一个sku的平台属性只有一个属性值
        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
        if (null!=attrList && attrList.size()>0){

            //循环获取数据
            List<SearchAttr> searchAttrList  = attrList.stream().map(baseAttrInfo -> {
                //创建一个SearchAttr对象
                SearchAttr searchAttr = new SearchAttr();
                //存储平台属性id
                searchAttr.setAttrId(baseAttrInfo.getId());
                //存储平台属性名
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                //存储平台属性值
                searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());

                //将当前的searchAttr返回
                return searchAttr;

            }).collect(Collectors.toList());

            //将平台属性数据放进goods中
            goods.setAttrs(searchAttrList);
        }
        //将数据保存到es中
        goodsRepository.save(goods);
    }

    /**
     * 根据商品的skuId下架商品列表
     * @param skuId
     */
    @Override
    public void lowerGoods(Long skuId) {
        //下架本质就是删除es中该sku的数据
        goodsRepository.deleteById(skuId);
    }

    /**
     * 根据商品的skuId 更新商品热点数据  暂存在redis中 每访问一次加一 达到 一定数值去es进行更新操作
     * @param skuId
     */
    @Override
    public void incrHotScore(Long skuId) {

        //定义zset 的 key
        String hotKey  = "hotScore";

        //在redis中保存数据  zset中的元素塞入之后，可以修改其score的值，通过 zincrby 来对score进行加/减；
        // 当元素不存在时，则会新插入一个 返回结果是修改后的评分score
        Double hotScore  = redisTemplate.opsForZSet().incrementScore(hotKey, "sku:" + skuId, 1);

        //判断score是否达到去es更新临界点
        if(hotScore%10==0){
            //去es更新数据 先根据sku查询出对象 再修改值 再上架保存（覆盖更新）
            Optional<Goods> optional = goodsRepository.findById(skuId);
            //获取查询项的数据
            Goods goods = optional.get();
            //修改评分值 四舍五入为整数 10的倍数
            goods.setHotScore(Math.round(hotScore));
            //保存 覆盖更新
            goodsRepository.save(goods);
        }
    }

    /**
     * 使用es进行搜索搜索列表
     * @param searchParam
     * @return
     * @throws IOException
     */
    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {

        /*
            1、制作该制作查询dsl语句
            2、执行该dsl语句  获取执行结果
            3、对执行结果进行封装 返回
        */

        //1、制作查询dsl语句
        SearchRequest searchRequest =  buildQueryDsl(searchParam);

        //2、引入操作es的客户端 执行dsl语句 搜索 获得返回结果
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        //3、对执行结果进行封装 返回SearchResponseVo数据 查询结果中有总条数 可在这个方法中为responseVO的total属性赋值
        SearchResponseVo responseVO = parseSearchResult(response);

        //设置最终返回结果的分页相关数据
        responseVO.setPageNo(searchParam.getPageNo());
        responseVO.setPageSize(searchParam.getPageSize());

        //获取一共多少页
        Long totalPages = (responseVO.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();
        //设置totalPages
        responseVO.setTotalPages(totalPages);

        //返回最终结果
        return responseVO;
    }

    // 制作dsl 语句
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 查询器：相当于dsl查询最外层的{}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 声明一个QueryBuilder 对象 query:bool
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 判断查询关键字
        if (StringUtils.isNotEmpty(searchParam.getKeyword())){
            // 创建QueryBuilder 对象
            // MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title",searchParam.getKeyword());
            //  boolQueryBuilder.must(matchQueryBuilder);
            //  demo: 用户查询荣耀手机的时候， es 1. 分词 [荣耀  手机] Operator.AND 表示这个title 中这两个字段都必须存在
            //  如果 Operator.OR 那么 title 中有其中一个即可！
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(title);
        }
        // 设置品牌： trademark= 2:华为  2=tmId 华为=tmName
        String trademark = searchParam.getTrademark();
        if (StringUtils.isNotEmpty(trademark)){
            // 不为空说明用户按照品牌查询
            String[] split = StringUtils.split(trademark, ":");
            // 判断分割之后的数据格式
            // select * from basetrademark id = ?
            if (split!=null && split.length==2){
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                boolQueryBuilder.filter(tmId);
            }
        }
        // terms，term
        // terms:表示范围取值 select * from where id in (1,2,4)
        // term:表示精确取值 select * from where id = ?
        // 设置分类Id 过滤 通过一级分类Id，二级分类Id，三级分类Id
        if (null!=searchParam.getCategory1Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id",searchParam.getCategory1Id()));
        }
        if (null!=searchParam.getCategory2Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }

        if (null!=searchParam.getCategory3Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }
        // 平台属性
        // props=23:4G:运行内存
        //平台属性Id 平台属性值名称 平台属性名
        // nested 将平台属性，属性值作为独立的数据查询
        String[] props = searchParam.getProps();
        if (null!=props && props.length>0){
            // 循环遍历
            for (String prop : props) {
                // prop = 23:4G:运行内存
                String[] split = StringUtils.split(prop, ":");
                // split判断分割之后的格式 是否正确
                if (null!=split && split.length==3){
                    // 构建查询语句
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // 匹配查询
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));

                    // 将subBoolQuery 放入boolQuery
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));
                    // 将boolQuery 放入总的查询器
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // 执行query 方法
        searchSourceBuilder.query(boolQueryBuilder);
        // 构建分页
        // 开始条数
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 排序 1:hotScore 2:price
        String order = searchParam.getOrder();
        if (StringUtils.isNotEmpty(order)){
            // 进行分割数据
            String[] split = StringUtils.split(order, ":");
            // 判断 1:hotScore | 3 | price
            if (null!=split && split.length==2){
                // 设置排序规则
                // 定义一个排序字段
                String field = null;
                switch (split[0]){
                    case "1":
                        field="hotScore";
                        break;
                    case "2":
                        field="price";
                        break;
                }
                searchSourceBuilder.sort(field,"asc".equals(split[1])? SortOrder.ASC:SortOrder.DESC);
            }else {
                // 默认走根据热度进行降序排列。
                searchSourceBuilder.sort("hotScore",SortOrder.DESC);
            }
        }

        // 设置高亮
        // 声明一个高亮对象，然后设置高亮规则
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");// 商品的名称高亮
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 设置聚合
        // 聚合品牌
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")  // 品牌Id
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName")) // 品牌名称
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));

        // 将聚合的规则添加到查询器
        searchSourceBuilder.aggregation(termsAggregationBuilder);
        // 平台属性
        // 设置nested 聚合。
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId") // 平台属性Id
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName")) // 平台属性名称
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        // 设置有效的数据，查询的时候哪些字段需要显示
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);

        //  GET /goods/info/_search
        // 设置索引库index，type
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        // 打印dsl 语句
        String query = searchSourceBuilder.toString();
        System.out.println("dsl:"+query);

        return searchRequest;

    }


    //制作最终返回结果集
    private SearchResponseVo parseSearchResult ( SearchResponse response){
        SearchResponseVo searchResponseVo = new SearchResponseVo();
//        private List<SearchResponseTmVo> trademarkList;
//        private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
//        private List<Goods> goodsList = new ArrayList<>();
//        private Long total;//总记录数
//        private Integer pageSize;//每页显示的内容
//        private Integer pageNo;//当前页面
//        private Long totalPages;

        // 品牌数据通过聚合得到的！
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        // 获取品牌Id Aggregation接口中并没有获取到桶的方法，所以在这进行转化
        // ParsedLongTerms 是他的实现。
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        // 从桶中获取数据
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            // 获取品牌的Id
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            searchResponseTmVo.setTmId(Long.parseLong(((Terms.Bucket) bucket).getKeyAsString()));

            // 获取品牌的名称
            Map<String, Aggregation> tmIdSubAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            // tmNameAgg 品牌名称的agg 品牌数据类型是String
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubAggregationMap.get("tmNameAgg");
            // 获取到品牌的名称并赋值
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            // 获取品牌的logo
            ParsedStringTerms tmlogoUrlAgg = (ParsedStringTerms) tmIdSubAggregationMap.get("tmLogoUrlAgg");
            String tmlogoUrl = tmlogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmlogoUrl);
            // 返回品牌
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        // 赋值品牌数据
        searchResponseVo.setTrademarkList(trademarkList);

        // 获取平台属性数据 应该也是从聚合中获取
        // attrAgg 数据类型是nested ，转化一下
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // 获取attrIdAgg 平台属性Id 数据
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        // 判断桶的集合不能为空
        if (null!=buckets && buckets.size()>0){
            // 循环遍历数据
            List<SearchResponseAttrVo> attrsList = buckets.stream().map(bucket -> {
                // 获取平台属性对象
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                searchResponseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                // 获取attrNameAgg 中的数据 名称数据类型是String
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                // 赋值平台属性的名称
                searchResponseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());

                // 赋值平台属性值集合 获取attrValueAgg
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();
                // 获取该valueBuckets 中的数据
                // 将集合转化为map ，map的key 就是桶key，通过key获取里面的数据，并将数据变成一个list集合
                List<String> valueList = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                searchResponseAttrVo.setAttrValueList(valueList);
                // 返回平台属性对象
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(attrsList);
        }

        // 获取商品数据 goodsList
        // 声明一个存储商品的集合
        List<Goods> goodsList = new ArrayList<>();
        // 品牌数据需要从查询结果集中获取。
        SearchHits hits = response.getHits(); //  "hits" : {
        SearchHit[] subHits = hits.getHits(); //  "hits" : [ { ...} ]
        if (null!=subHits&& subHits.length>0){
            // 循环遍历数据
            for (SearchHit subHit : subHits) {
                // 获取商品的json 字符串
                String goodsJson = subHit.getSourceAsString();
                // 直接将json 字符串变成Goods.class
                Goods goods = JSONObject.parseObject(goodsJson, Goods.class);
                // 获取商品的时候，如果按照商品名称查询时，商品的名称显示的时候，应该高亮。但是，现在这个名称不是高亮
                // 从高亮中获取商品名称
                if (subHit.getHighlightFields().get("title")!=null){
                    // 说明当前用户查询是按照全文检索的方式查询的。
                    // 将高亮的商品名称赋值给goods
                    // [0] 因为高亮的时候，title 对应的只有一个值。
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                // 添加商品到集合
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);
        // 设置总记录数
        searchResponseVo.setTotal(hits.totalHits);
        return searchResponseVo;
    }

}
