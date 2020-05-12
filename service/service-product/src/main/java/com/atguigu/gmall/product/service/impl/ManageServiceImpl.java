package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ldh
 * @create 2020-04-17 14:58
 */
@Service
@Transactional //对此service开启声明式事务
public class ManageServiceImpl implements ManageService {

    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private  SpuInfoMapper spuInfoMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Autowired
    private RabbitService rabbitService;




    /**
     * 查询所有的一级分类信息
     * @return
     */
    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }

    /**
     * 根据一级分类Id 查询二级分类数据
     * @param category1Id
     * @return
     */
    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        // select * from baseCategory2 where Category1Id = ?
        QueryWrapper<BaseCategory2> queryWrapper = new QueryWrapper();
        queryWrapper.eq("category1_id",category1Id);
        List<BaseCategory2> baseCategory2List = baseCategory2Mapper.selectList(queryWrapper);
        return baseCategory2List;

    }

    /**
     * 根据二级分类Id 查询三级分类数据
     * @param category2Id
     * @return
     */
    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        // select * from baseCategory3 where Category2Id = ?
        QueryWrapper<BaseCategory3> queryWrapper = new QueryWrapper();
        queryWrapper.eq("category2_id",category2Id);
        return baseCategory3Mapper.selectList(queryWrapper);

    }


    /**
     * 根据分类Id 获取各分类平台属性数据
     * 接口说明：
     *      1，平台属性可以挂在一级分类、二级分类和三级分类
     *      2，查询一级分类下面的平台属性，传：category1Id，0，0；   取出该分类的平台属性
     *      3，查询二级分类下面的平台属性，传：category1Id，category2Id，0；
     *         取出对应一级分类下面的平台属性与二级分类对应的平台属性
     *      4，查询三级分类下面的平台属性，传：category1Id，category2Id，category3Id；
     *         取出对应一级分类、二级分类与三级分类对应的平台属性
     *
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     */
    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }

    /**
     * 保存平台属性方法
     * @param baseAttrInfo
     * @return
     */
    @Override
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        //区分什么时候是新增修改
        if(baseAttrInfo.getId()!=null){
            //修改 update
            baseAttrInfoMapper.updateById(baseAttrInfo);

        }else{
            //往base_attr_info : 平台属性
            // base_attr_value ：平台属性值！ 两张表中插入数据
            baseAttrInfoMapper.insert(baseAttrInfo);
        }

        //因为修改不能确定用户倒底修改了那个属性值所以我们先删除在重新添加,前台在将baseAttrInfo传过来时已将携带了完整的属性值数据
        QueryWrapper<BaseAttrValue> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_id",baseAttrInfo.getId());
        baseAttrValueMapper.delete(wrapper);


        //获取要添加属性的属性值集合
        List<BaseAttrValue> attrValueList =  baseAttrInfo.getAttrValueList();

        //判断baseAttrInfo的attrValueList属性 属性值集合是否为空
        if(attrValueList!=null && attrValueList.size()>0){

            for (BaseAttrValue baseAttrValue : attrValueList) {
                //设置属性值对象的AttrId属性值 获取baseAttrInfo的自增长键值
                //baseAttrInfo插入成功后可通过 baseAttrInfo.getId()获取其自动增长的键值
                baseAttrValue.setAttrId(baseAttrInfo.getId());

                baseAttrValueMapper.insert(baseAttrValue);
            }
        }
    }

    /**
     * 根据平台属性id attrId 查询平台属性值
     * @param attrId
     * @return
     */
    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        //先查询平台属性对象
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);

        //获取平台属性值
        baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        return baseAttrInfo;
    }

    //根据平台属性id获得平台属性值集合
    private List<BaseAttrValue> getAttrValueList(Long attrId) {
        QueryWrapper<BaseAttrValue> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_id",attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.selectList(wrapper);
        return baseAttrValueList;
    }

    /**
     * 根据三级分类id 分页查询该分类下的spu商品信息
     * @param infoPage
     * @param spuInfo
     * @return
     */
    @Override
    public IPage<SpuInfo> selectPage(Page<SpuInfo> infoPage, SpuInfo spuInfo) {

        //创建查询条件
        QueryWrapper<SpuInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("category3_id",spuInfo.getCategory3Id());
        wrapper.orderByDesc("id");

        //进行分页查询
        IPage<SpuInfo> spuInfoIPage = spuInfoMapper.selectPage(infoPage, wrapper);

        return spuInfoIPage;
    }

    // 添加spu页面 加载全部销售属性 请求URLhttp://api.gmall.com/admin/product/baseSaleAttrList
    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {

        List<BaseSaleAttr> baseSaleAttrList = baseSaleAttrMapper.selectList(null);
        return baseSaleAttrList;
    }

    /**
     * 保存spu 商品信息
     * @param spuInfo
     * @return
     * 添加spu 请求URL http://api.gmall.com/admin/product/saveSpuInfo
     */
    @Override
    public void saveSpuInfo(SpuInfo spuInfo) {
        //1、保存信息到 spuInfo: 商品表
        spuInfoMapper.insert(spuInfo);

        //2、保存信息到 spuSaleAttr: 销售属性表：
        //2.1 获取该spu商品的销售属性集合
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();

        //2.2 判断该商品spu是否具有销售属性
        if(spuSaleAttrList!=null && spuSaleAttrList.size()>0){
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {

                //2.3 前台没有提供spuId ,获取上面保存后自动生成的spuId
                spuSaleAttr.setSpuId(spuInfo.getId());

                //2.4 往spuSaleAttr: 销售属性表中保存商品的销售属性数据
                spuSaleAttrMapper.insert(spuSaleAttr);

                //3、保存销售属性值到 puSaleAttrValue: 销售属性值表中
                //3.1 获取当前循环销售属性的销售属性值集合
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();

                //3.2 判断该销售属性是否具有销售属性值
                if(spuSaleAttrValueList!=null && spuSaleAttrValueList.size()>0){

                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        //每一个销售属性对象中都会有一个该销售属性的属性值集合属性
                        // 页面提交的数据没有销售属性名和spuId，销售属性值名在销售属性值集合中有
                        // 所以要再这里再起一个循环，获取上面的销售属性名和spuId，保存每一个销售属性的销售值
                        //3.2对每一个销售属性值 都要先获取销售属性值的spuId 销售属性名 saleAttrName
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());

                        //3.3保存销售属性值到 puSaleAttrValue: 销售属性值表中
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }

        //4、保存商品spu图片信息到spuImage: 商品的图片表
        //4.1 获取商品spu图片集合
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();

        //4.2判断商品spu图片集合是否为空
        if(spuImageList!=null && spuImageList.size()>0){

            for (SpuImage spuImage : spuImageList) {
                //4.3 前台没有提供spuId ,获取上面保存后自动生成的spuId
                spuImage.setSpuId(spuInfo.getId());

                //4.4 保存商品spu图片信息到spuImage: 商品的图片表
                spuImageMapper.insert(spuImage);
            }
        }

    }


    /**
     * 根据spuId 查询该spu下的所有图片集合spuImageList
     * @param spuId
     * @return
     */
    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        //创建查询条件
        QueryWrapper<SpuImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("spu_id",spuId);
        return spuImageMapper.selectList(queryWrapper);

    }

    /**
     * 根据spuId 查询该spu商品下的所有的销售属性
     * @param spuId
     * @return
     */
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {

        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }


    /**
     * 添加保存sku 将前台传来的数据保存在四张表中
     * @param skuInfo
     * @return
     */
    @Override
    public void saveSkuInfo(SkuInfo skuInfo) {
        //1、先保存数据到sku_info 表
        skuInfoMapper.insert(skuInfo);

        //2、获取sku图片集合 保存到spu_image 表中
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        //2.1 判断skuImageList是否为空
        if(skuImageList!=null && skuImageList.size()>0){
            for (SkuImage skuImage : skuImageList) {
                //2.2 获取上面保存skuInfo的sku_id
                skuImage.setSkuId(skuInfo.getId());

                //2.3 保存SkuImage
                skuImageMapper.insert(skuImage);
            }
        }


        //3、获取skuAttrValueList sku平台属性值集合 保存到sku_attr_value 表中
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        //3.1 判断skuAttrValueList是否为空
        if(skuAttrValueList!=null && skuAttrValueList.size()>0){
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {

                //3.2 获取上面保存skuInfo的sku_id
                skuAttrValue.setSkuId(skuInfo.getId());

                //3.3 保存sku的平台属性值
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }


        //4、获取skuSaleAttrValueList sku销售属性值集合 保存到sku_sale_attr_value 表中
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        //4.1 判断skuSaleAttrValueList是否为空
        if(skuSaleAttrValueList!=null && skuSaleAttrValueList.size()>0){
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                //4.2获取skuInfo 的spuId
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());

                //4.3 获取上面保存skuInfo的sku_id
                skuSaleAttrValue.setSkuId(skuInfo.getId());

                //4.4 保存sku的销售属性值
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }

        //发送消息给es 商品上架
        //商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuInfo.getId());

    }

    /**
     * 分页查询所有sku列表
     * @param skuInfoPage
     * @return
     */
    @Override
    public IPage<SkuInfo> selectSkuInfoList(Page<SkuInfo> skuInfoPage) {
        //创建查询条件
        QueryWrapper<SkuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");

        //分页查询
        IPage<SkuInfo> skuInfoIPage = skuInfoMapper.selectPage(skuInfoPage, queryWrapper);

        return skuInfoIPage;
    }

    /**
     * 根据skuId 上架sku  本质就是更改sku_info表is_sale状态 0下架  1上架
     * @param skuId
     * @return
     */
    @Override
    public void onSale(Long skuId) {
        //根据skuId获取要修改的SkuInfo对象
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        //修改is_sale的值
        skuInfo.setIsSale(1);

        //保存修改
        skuInfoMapper.updateById(skuInfo);

        //发送消息给rabbit 商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);

    }

    /**
     * 根据skuId 下架sku  本质就是更改sku_info表is_sale状态 0下架  1上架
     * @param skuId
     * @return
     */
    @Override
    public void cancelSale(Long skuId) {
        //根据skuId获取要修改的SkuInfo对象
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        //修改is_sale的值
        skuInfo.setIsSale(0);

        //保存修改
        skuInfoMapper.updateById(skuInfo);

        //商品下架 发送rabbit消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_LOWER, skuId);

    }

    /**
     *为前台模块提供商品数据 只供内部调用访问
     * 根据skuId获取sku基本信息
     * @param skuId
     * @return
     */
    @Override
    @GmallCache(prefix = "getSkuInfo:")
    public SkuInfo getSkuInfo(Long skuId) {

        // 使用redission 获取分布式锁 查询数据添加缓存
       //return getSkuInfoRedisson(skuId);
        return getSkuInfoDB(skuId);
    }

    // 使用redission 获取分布式锁 查询数据添加缓存
    private SkuInfo getSkuInfoRedisson(Long skuId) {
        //定义返回结果对象
        SkuInfo skuInfo = null;

        try {
            //定义存储商品{sku} key = sku:skuId:info去缓存中获取数据
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 根据key去缓存中获取数据获取数据
            skuInfo = (SkuInfo)redisTemplate.opsForValue().get(skuKey);

            //整合流程  判断是否从缓存中获取到了数据
            if(skuInfo==null){
                //缓存中没有去数据库中获取，使用redis实现的分布式锁控制只有一个线程去数据库中查数据
                // 定义分布式锁的lockKey=sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                //使用redission根据key生成分布式锁
                RLock lock = redissonClient.getLock(lockKey);
                // 准备上锁
                /*
                lock.lock();
                lock.lock(10, TimeUnit.SECONDS);
                boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);
                 */
                boolean isLock = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);

                //判断是否获得锁
                if(isLock){

                    try {
                        // 获取到了分布式锁，走数据库查询数据并放入缓存。
                        skuInfo =  getSkuInfoDB(skuId);

                        // 判断数据库中的数据是否为空
                        if(null==skuInfo){
                            // 为了防止缓存穿透，赋值一个空对象放入缓存。最好这个空对象的过期时间不要太长。
                            SkuInfo skuInfo1 = new SkuInfo();
                            redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            //将空对象返回
                            return skuInfo1;
                        }
                        //从数据库中查到的数据不为空 将数据放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo;

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        //执行完毕解锁
                        lock.unlock();
                    }

                }else{
                    try {
                        //未获得锁等待
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //等待时间到达后继续执行任务
                    return getSkuInfo(skuId);
                }
            }else{
                //缓存中有，走缓存 ，从缓存获取数据
                //判断从缓存中获得的对象是否有数据
                // 如果用户查询一个在数据库中根本不存在的数据时，那么我们存储一个空对象放入了缓存。
                // 实际上我们应该想要获取的是不是空对象，并且对象的属性也是有值的！
                /*if(null == skuInfo.getId()){

                    return null;
                }*/
                //返回缓存中获取的数据
                return skuInfo;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 如果上述执行过程出现异常 如缓存宕机了，那么我先让应用程序访问数据库。
        return getSkuInfoDB(skuId);
    }

    // 使用redis 获取分布式锁 查询数据添加缓存
    private SkuInfo getSkuInfoRedis(Long skuId) {
        //定义返回结果对象
        SkuInfo skuInfo = null;

        try {
            //定义存储商品{sku} key = sku:skuId:info去缓存中获取数据
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 根据key去缓存中获取数据获取数据
            skuInfo = (SkuInfo)redisTemplate.opsForValue().get(skuKey);

            //整合流程  判断是否从缓存中获取到了数据
            if(null==skuInfo){
                //缓存中没有去数据库中获取，使用redis实现的分布式锁控制只有一个线程去数据库中查数据
                // 定义分布式锁的lockKey=sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;

                //获取随机字符串为lockKey的value
                String uuid = UUID.randomUUID().toString();
                // 为了防止缓存击穿，执行获取分布式锁的命令 执行成功则获取到分布式锁
                Boolean isExist = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                //判断是否获得锁
                if(isExist){
                    // 获取到了分布式锁，走数据库查询数据并放入缓存。
                    System.out.println("获取到了分布式锁，走数据库查询数据并放入缓存。");
                    skuInfo =  getSkuInfoDB(skuId);

                    // 判断数据库中的数据是否为空
                    if(null==skuInfo){
                        // 为了防止缓存穿透，赋值一个空对象放入缓存。最好这个空对象的过期时间不要太长。
                        SkuInfo skuInfo1 = new SkuInfo();
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        //将空对象返回
                        return skuInfo1;
                    }
                    //从数据库中查到的数据不为空 将数据放入缓存
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                    //使用lua脚本删除锁
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    //创建默认redis脚本对象
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script);
                    //设置脚本执行结果的返回值类型
                    redisScript.setResultType(Long.class);
                    // 执行脚本 根据锁的key 找锁的值，进行删除
                    redisTemplate.execute(redisScript, Arrays.asList(lockKey),uuid);

                    // 返回数据
                    return skuInfo;
                }else {
                    try {
                        //未获得锁等待
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //等待时间到达后继续执行任务
                    return getSkuInfo(skuId);
                }

            }else{
                //缓存中有，走缓存 ，从缓存获取数据
                //判断从缓存中获得的对象是否有数据
                // 如果用户查询一个在数据库中根本不存在的数据时，那么我们存储一个空对象放入了缓存。
                // 实际上我们应该想要获取的是不是空对象，并且对象的属性也是有值的！
                if(null == skuInfo.getId()){

                    return null;
                }
                //返回缓存中获取的数据
                return skuInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 如果上述执行过程出现异常 如缓存宕机了，那么我先让应用程序访问数据库。
        return getSkuInfoDB(skuId);
    }

    //根据skuId查询数据库中的数据
    private SkuInfo getSkuInfoDB(Long skuId) {

        //根据skuId获取SkuInfo基本数据
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        //判断skuInfo是否为空，不为空再查询它的图片  若skuInfo为空则下面获取它的图片集合出现空指针
        if(skuInfo!=null){
            //根据skuId获取skuimg图片信息
            QueryWrapper<SkuImage> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("sku_id",skuId);
            List<SkuImage> skuImageList = skuImageMapper.selectList(queryWrapper);

            //将sku图片集合赋值给skuInfo
            skuInfo.setSkuImageList(skuImageList);
        }
        //将查询到的skuInfo数据返回
        return skuInfo;
    }


    /**
     * 通过三级分类id查询三级分类信息
     * @param category3Id
     * @return
     */
    @Override
    @GmallCache(prefix = "getCategoryViewByCategory3Id:")
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {

        return baseCategoryViewMapper.selectById(category3Id);
    }

    /**
     * 跟据前端传过来的skuId单独获取sku价格信息
     * @param skuId
     * @return
     */
    @Override
    @GmallCache(prefix = "getSkuPriceBySkuId:")
    public BigDecimal getSkuPriceBySkuId(Long skuId) {
        //根据skuId查询skuInfo
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        // 判断是否有skuInfo只要价格其他信息不需要
        if(null != skuInfo){
            //返回skuInfo的价格信息
            return skuInfo.getPrice();

        }else{
            // 返回初始值
            return new BigDecimal("0");
        }
    }

    /**
     * 根据spuId，skuId 查询该spu所有的销售属性及销售属性值，并根据skuId高亮显示该sku的销售属性值（即选中状态）
     * @param skuId
     * @param spuId
     * @return
     */
    @Override
    @GmallCache(prefix = "getSpuSaleAttrListCheckBySku:")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long spuId, Long skuId) {

        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(spuId,skuId);
    }

    /**
     * 根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
     * @param spuId
     * @return
     */
    @Override
    @GmallCache(prefix = "getSkuValueIdsMap:")
    public Map getSkuValueIdsMap(Long spuId) {

        //根据spuId 查询该spu下所有的sku的销售属性值  以所有的销售属性值id为key skuid为value 封装为一个map 集合属性
        List<Map> mapList  = skuSaleAttrValueMapper.getSkuValueIdsMap(spuId);

        //创建结果集
        HashMap<Object, Object> hashMap = new HashMap<>();

        //将查询结构集mapList转换为结果集hashMap
        if(mapList != null && mapList.size()>0){
            for (Map map : mapList) {
                hashMap.put(map.get("value_ids"),map.get("sku_id"));
            }
        }
        return hashMap;
    }

    /**
     * 获取全部一二三级分类信息 用于首页展示
     * @return
     */
    @Override
    @GmallCache(prefix = "BaseCategoryList:")
    public List<JSONObject> getBaseCategoryList(){
         /*
            1.  先获取到所有的分类数据 一级，二级，三级分类数据
            2.  开始组装数据 组装条件就是分类Id 为主外键
            3.  将组装的数据封装到 List<JSONObject> 数据中！
         */

        //创建最终返回集合
        List<JSONObject> list = new ArrayList<>();

        //先获取到所有分类数据 一级 二级 三级
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);

        //按照一级分类id进行分组，组名为一级分类名  Long为一级分类 List为id为long的集合
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));

        //定义index，用于标记一级分类序号 循环外定义 每循环一次index+1
        int index = 1;

        // 获取一级分类下所有数据 最外层循环 每循环一次一个一级分类的所有数据获取完成（包括它的二级 以及二级的三级）
        for (Map.Entry<Long, List<BaseCategoryView>> entry : category1Map.entrySet()) {
            //创建容纳一级分类数据JSONObject对象  category1中是包含了index  categoryName categoryId categoryChild数据
            JSONObject category1 = new JSONObject();

            //获取当前一级分类的id categoryId
            Long category1Id = entry.getKey();

            //获取当前循环的map的value id为Long类型数值的BaseCategoryView集合 也是当前一级分类下所有的二级分类对象集合
            List<BaseCategoryView> category2List = entry.getValue();

            //获取当前一级分类的categoryName
            String category1Name = category2List.get(0).getCategory1Name();

            //将数据放入category1中
            category1.put("index",index);
            category1.put("categoryId",category1Id);
            category1.put("categoryName",category1Name);

            //迭代index 每一个一级分类JSONObject 都有一个index
            index++;

            //接下来准备当前一级分类下的所二级分类数据 categoryChild数据------------------------------------------------------
            //创建容纳所有二级分类数据的集合 准备二级分类数据 ，所有二级分类数据就是一级分类的categoryChild！
            List<JSONObject> category2Child = new ArrayList<>();

            //按照当前一级分类下的二级分类id进行分组，组名为二级分类名  Long为二级分类id类型 List为id为long的集合
            Map<Long, List<BaseCategoryView>> category2Map = category2List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));

            // 获取二级分类下所有数据 最外层循环 每循环一次一个二级分类的所有数据获取完成（包括该二级下的所有的三级）
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {

                //创建容纳单个二级分类数据JSONObject对象  category2中是包含了  categoryName categoryId categoryChild数据
                JSONObject category2 = new JSONObject();

                //获取当前二级分类的id categoryId
                Long category2Id = entry2.getKey();

                //获取当前循环的map的value id为Long类型数值的BaseCategoryView集合 也是当前二级分类下所有的三级分类对象集合
                List<BaseCategoryView> category3List = entry2.getValue();

                //获取当前二级分类的categoryName
                String category2Name = category3List.get(0).getCategory2Name();

                //将数据放入category2中
                category2.put("categoryId",category2Id);
                category2.put("categoryName",category2Name);

                //接下来准备当前二级分类下的所有三级分类数据 categoryChild数据------------------------------------------------------
                //创建容纳所有三级分类数据的集合 准备三级分类数据 ，所有三级分类数据就是二级分类的categoryChild！
                List<JSONObject> category3Child = new ArrayList<>();

                //由于三级分类都是单独的所有不用分组聚合成map了  category3View是每次循环的集合对象单个的BaseCategoryView对象
                category3List.stream().forEach((category3View)->{
                    //创建容纳单个三级分类数据JSONObject对象存放当前单个三级分类数据  category3中是包含了  categoryName categoryId 数据
                    JSONObject category3 = new JSONObject();

                    //获取当前三级分类的id categoryId
                    Long category3Id = category3View.getCategory3Id();

                    //获取当前三级分类的categoryName
                    String category3Name = category3View.getCategory3Name();

                    //将当前三级分类数据存放进category3中
                    category3.put("categoryId",category3Id);
                    category3.put("categoryName",category3Name);

                    //将当前的JSONObject对象category3存放进 所有三级分类数据的集合category3Child中
                    category3Child.add(category3);

                });

                //获取到当前二级分类下的所有三级分类信息 为一个集合 category3Child
                //将category3Child 存放进当前单个二级分类数据中
                category2.put("categoryChild",category3Child);

                //组合完当前循环的单个二级分类对象数据   将其放入所有单个二级分类的集合中
                category2Child.add(category2);
            }

            //获取到当前一级分类下的所有二级分类信息 为一个集合 category2Child
            //category2Child 存放进当前单个一级分类数据中
            category1.put("categoryChild",category2Child);

            //组合完当前循环的单个一级分类对象数据   将其放入所有单个一级分类的集合 category1Child中
            list.add(category1);
        }

        return list;
    }

    /**
     * 通过skuinfo中的品牌Id 来查询该sku的品牌数据  用于es封装数据
     * @param tmId
     * @return
     */
    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {

        return baseTrademarkMapper.selectById(tmId);
    }

    /**
     * 通过商品的skuId 来查询该sku的平台属性数据（属性名属性值 关联多个表）
     * @param skuId
     * @return
     */
    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {

        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }

}
