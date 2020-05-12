package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ldh
 * @create 2020-04-29 8:30
 */
@Controller
@RequestMapping
public class ListController {

    @Autowired
    private ListFeignClient listFeignClient;


    /**
     * 使用es进行搜索搜索列表
     * @param searchParam
     * @return
     *
     */
    @GetMapping("list.html")
    public String search (SearchParam searchParam, Model model){
        //调用list模块 得到搜索sku信息
        Result<Map> result =  listFeignClient.list(searchParam);
        model.addAllAttributes(result.getData());

        //保存用户的查询参数数据，用于前端显示
        model.addAttribute("searchParam",searchParam);

        //拼接检索后的商品列表带参数的url 实现多个查询参数并列查询 主要作用是记录拼接参数列表
        String urlParam = makeUrlParam(searchParam);
        model.addAttribute("urlParam",urlParam);

        //面包屑品牌处理 按品牌筛选后品牌条件回显
        String trademarkParam  = makeTrademark(searchParam.getTrademark());
        model.addAttribute("trademarkParam",trademarkParam);

        //面包屑平台属性处理 在商品列表页点击按平台属性筛选后回显平台属性
        List<Map<String, String>> propsParamList  = makeProps(searchParam.getProps());
        model.addAttribute("propsParamList",propsParamList);
        
        //显示的商品排序处理  默认按照综合即热度排序  可选按照价格进行排序 用于前端展示排序规格
        Map<String, Object> orderMap = dealOrder(searchParam.getOrder());
        model.addAttribute("orderMap",orderMap);


        return "list/index";
    }

    //拼接检索后的商品列表参数url 显示在页面地址栏 加上控制器路径（因为前段没加 所以后台加） 实现多个查询参数并列查询
    private String makeUrlParam(SearchParam searchParam) {

        //用可变字符串拼接
        StringBuilder urlParam = new StringBuilder();

        //判断关键字是否为空
        if(searchParam.getKeyword()!=null){
            //拼接关键字参数
            urlParam.append("keyword=").append(searchParam.getKeyword());
        }

        //判断是否有一二三及分类id
        if (searchParam.getCategory1Id()!=null){
            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }
        // 判断二级分类
        if (searchParam.getCategory2Id()!=null){
            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
        }
        // 判断三级分类
        if (searchParam.getCategory3Id()!=null){
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }

        //判断有没有品牌参数 非第一位的参数要加&
        if(searchParam.getTrademark()!=null &&searchParam.getTrademark().length()>0){
            //判断前面有参数再拼接
            if (urlParam.length() > 0){
                urlParam.append("&trademark=").append(searchParam.getTrademark());
            }
        }

        //获取平台属性
        String[] paramProps = searchParam.getProps();
        //判断有没有平台属性参数
        if(null!=paramProps && paramProps.length>0){
            for (String paramProp : paramProps) {
                //判断前面有参数再拼接
                if (urlParam.length() > 0){
                    urlParam.append("&props=").append(paramProp);
                }
            }
        }

        //返回才控制器的路径的拼接参数
        return "list.html?" + urlParam.toString();
    }

    /**
     * 处理品牌条件回显  面包屑 在商品列表页点击按品牌筛选后回显品牌
     * @param trademark
     * @return
     */
    private String makeTrademark(String trademark) {

        //判断品牌数据是否为空 品牌id：品牌名
        if(null != trademark && trademark.length()>0){
            //将trademark 按：进行拆分
            String[] split = StringUtils.split(trademark, ":");
            //判断拆分是否成功
            if(null!=split && split.length==2){
                return "品牌：" + split[1];
            }
        }
        return "";
    }

    /**
     * 面包屑平台属性处理 在商品列表页点击按平台属性筛选后回显平台属性
     * @param props
     * @return
     */
    private List<Map<String, String>> makeProps(String[] props) {
        //创建最终返回的平台属性和值的结果集
        List<Map<String, String>> list = new ArrayList<>();

        //判断选择的平台属性集合是否为空 每一个平台属性值字符串是 2:v:n  id：值：名
        if(null!=props && props.length>0){
            //遍历数组
            for (String prop : props) {
                //对数组中的每一个2:v:n 值进行拆分
                //String[] split = StringUtils.split(prop, ":");
                String[] split = prop.split(":");


                //判断拆分是否正确
                if (split!=null && split.length==3){
                    // 声明一个map 存放该平台属性相关数据
                    HashMap<String, String> map = new HashMap<String,String>();
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrName",split[2]);
                    list.add(map);
                }
            }
        }
        //返回选择的平台属性集合
        return list;
    }

    /**
     * 显示的商品排序处理  默认按照综合即热度排序  可选按照价格进行排序 用于前端展示排序规格
     * 1:hotScore 2:price  1 2是type
     * @param order
     * @return
     */
    private Map<String, Object> dealOrder(String order) {
        //创建最终返回的ma
        Map<String,Object> orderMap = new HashMap<>();

        //判断order是否为空
        if(!StringUtils.isEmpty(order)) {
            String[] split = StringUtils.split(order, ":");

            //判断拆分是否成功
            if (split != null && split.length == 2) {
                //将传递的字段存放进map中
                orderMap.put("type",split[0]);
                //升序还是降序
                orderMap.put("sort",split[1]);
            }
        }else{
            //若没有点击选择排序规则则默认按照综合 1：asc
            orderMap.put("type", "1");
            orderMap.put("sort", "asc");
        }
        return orderMap;
    }



}
