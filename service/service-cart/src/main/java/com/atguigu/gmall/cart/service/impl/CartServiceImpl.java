package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ldh
 * @create 2020-04-30 16:12
 */
@Service
@Transactional
public class CartServiceImpl implements CartService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;
    /*
        添加购物车判断购物车中是否有该商品
        true: 数量相加
        false: 直接添加

        特殊处理：添加购物车的时候，直接将购物车添加到缓存中。
     */

    // 添加购物车 用户Id，商品Id，商品数量。 添加购物车方法的用户可以是登录用户 也可以是临时用户
    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        //根据userId创建存放在缓存中的购物车key
        String  cartKey = getCartKey(userId);

        /**
         *先判断缓存中是否有cartKey，没有的话先加载数据库中的数据放入缓存！
         * 向购物车中添加新商品前，判断该用户在缓存中是否有数据，（历史购物车数据可能在缓存中以过期）
         *没有就去数据库查该用户的历史购物车数据，然后将历史购物车数据存放进缓存中，下面再将新商品添加到购物车中，
         *这样最后就可以从缓存中得到用户完整的购物车信息
         */
        if (!redisTemplate.hasKey(cartKey)) {
            //去数据库查询该用户的购物车信息并添加到缓存（该用户的历史购物车信息）
            loadCartCache(userId);
        }

        //1、先根据用户id和skuId去数据库购物车表中查询该商品
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",userId).eq("sku_id",skuId);
        CartInfo cartInfoExist  = cartInfoMapper.selectOne(wrapper);

        //2、判断数据库cart表中是否有该商品
        if(null!=cartInfoExist ){
            //2.1 若有则该商品数量加1
            cartInfoExist .setSkuNum(cartInfoExist .getSkuNum()+skuNum);

            //2.2 商品价格更新为该商品的最新价格
            cartInfoExist .setSkuPrice( productFeignClient.getSkuPrice(skuId));

            //2.3向数据库中更新数据
            cartInfoMapper.updateById(cartInfoExist );
        }else{
            //3、若没有则将新商品添加到数据库购物车表中 新添加的商品 添加价格和实时价格相同
            //3.1 创建一个CartInfo对象 接收新商品的值
            CartInfo cartInfo = new CartInfo();
            //3.2 根据skuid该查询该商品的信息
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);

            //3.3 为cartInfo属性赋值
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            cartInfo.setSkuNum(skuNum);
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());

            //2.3向数据库中更新数据
            cartInfoMapper.insert(cartInfo);

            //若走这里则数据库中没有 这里将cartInfo赋值给cartInfoExist 在下面统一存放进缓存中
            cartInfoExist = cartInfo;
        }
        //4、将新添加的商品存放到缓存中 hmap存储用户的整个购物车 （覆盖更新）
        // key = user:userId:cart  map 为 field = skuId value = cartInfo(字符串) 往map中存放数据相同的field会覆盖
        // hset(key,field,value)    redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);

        //5、设置购物车缓存数据过期时间
        setCartKeyExpire(cartKey);

    }

    //设置购物车缓存数据过期时间
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey,RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);

    }

    //根据userId创建存放在缓存中的购物车key
    private String getCartKey(String userId) {
        //定义key user:userId:cart
        return RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX ;
    }

    /**
     * 通过用户Id 查询该用户的购物车列表 用户可能是登录用户 也可能是临时用户
     * @param userId
     * @param userTempId
     * @return
     */
    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        //1、创建最终返回的购物车集合 （集合就是购物车，集合中的每一个元素就是该用户的商品）
        List<CartInfo> cartInfoList  = new ArrayList<>();

        //未登录：根据临时用户Id 获取未登录的购物车数据
        if(StringUtils.isEmpty(userId)){
            cartInfoList = getCartList(userTempId);
            return cartInfoList;
        }

        //已登录：根据登录用户Id 获取登录用户的购物车数据 对未登录购物车进行合并
        if(!StringUtils.isEmpty(userId)){
            /**
                1. 准备合并购物车
                2. 获取未登录的购物车数据
                3. 如果未登录购物车中有数据，则进行合并 合并的条件：skuId 相同 则数量相加，不同则直接添加数据
                     合并完成之后，删除未登录的数据！
                4. 如果未登录购物车没有数据，则直接显示已登录的数据
             */
            //1. 根据userTempId查询未登录购物车数据
            List<CartInfo> cartTempList = getCartList(userTempId);

            //2. 判断未登录购物车cartTempList中是否有数据
            if(!CollectionUtils.isEmpty(cartTempList)){
                //不为空 登录+未登录 进行合并购物车
                cartInfoList = mergeToCartList(cartTempList,userId);

                //删除数据库和缓存中的临时用户购物车信息
                deleteCartList(userTempId);
            }

            if(CollectionUtils.isEmpty(cartTempList) || StringUtils.isEmpty(userTempId)){
                //没有临时用户的购物车数据 不用合并购物车 直接返回登录用户的购物车数据
                cartInfoList = getCartList(userId);
            }
        }
        //返回最终的购物车数据
        return cartInfoList;
    }

    //不为空 登录+未登录 进行合并购物车
    private List<CartInfo> mergeToCartList(List<CartInfo> cartTempList, String userId) {
          /*
        demo1:
            登录：
                37 1
                38 1
            未登录：
                37 1
                38 1
                39 1
            合并之后的数据
                37 2
                38 2
                39 1
         demo2:
             未登录：
                37 1
                38 1
                39 1
                40 1
              合并之后的数据
                37 1
                38 1
                39 1
                40 1
         */
        // 合并登录+未登录
        // 1、通过用户Id 获取登录购物车数据
        List<CartInfo> cartLoginList = getCartList(userId);

        // 2、以skuId为key ，以 cartInfo 为value 将cartLoginList转化为一个map集合，方便判断是否包含。
        Map<Long, CartInfo> cartInfoMapLogin = cartLoginList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));

        //3、遍历cartTempList 判断cartInfoMapLogin中是否包含cartTempList中的商品 包含直接加数量 不包含直接添加
        // 循环未登录购物车数据
        for (CartInfo tempCartInfo : cartTempList) {
            //获取未登录购物车中当前循环的商品skuId
            Long skuId = tempCartInfo.getSkuId();

            //判断cartInfoMapLogin中是否包含cartTempList中当前循环的商品
            if(cartInfoMapLogin.containsKey(skuId)){
                // 未登录购物车中含有 已登录购物车中已有的商品  在cartInfoMapLogin获取该商品
                CartInfo cartInfoLogin = cartInfoMapLogin.get(skuId);

                //该商品的数量增加
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum()+tempCartInfo.getSkuNum());

                //细节问题 若未登录购物车中商品是选中状态 则合并后该商品也应该是选中状态
                if(tempCartInfo.getIsChecked().intValue()==1){
                    cartInfoLogin.setIsChecked(tempCartInfo.getIsChecked());
                }

                //更新该商品数据到数据库
                cartInfoMapper.updateById(cartInfoLogin);
            }else {
                //cartInfoMapLogin中不包含cartTempList中当前循环的商品  直接添加
                //修改当前循环商品的用户id为已登录用户id （商品贷临时用户id改为真正的登录用户id）
                tempCartInfo.setUserId(userId);

                //将该商品数据插入到购物车数据库中
                cartInfoMapper.insert(tempCartInfo);
            }
        }
        //去数据库中重新查询合并后的该用户的购物车信息 根据用户Id 查询数据库中的购物车数据，并添加到缓存！
        //数据库中查询用户购物车信息会将购物车数据存放到缓存中（这里会将合并后的购物车数据存放到缓存中覆盖更新）
        List<CartInfo> cartInfoList = loadCartCache(userId);
        return cartInfoList;
    }

    //删除数据库和缓存中的临时用户购物车信息
    private void deleteCartList(String userTempId) {
        // 未登录购物车数据：一个是存在缓存，一个是存在数据库 都要进行删除。
        // 删除数据，对数据进行 DML 操作 DML:insert,update,delete
        // 先删缓存 获取 购物车的key
        String cartKey = getCartKey(userTempId);

        //判断缓存中是否有临时用户的购物车信息
        Boolean aBoolean = redisTemplate.hasKey(cartKey);
        if(aBoolean){
            //有则删除缓存中的临时用户的购物车信息
            redisTemplate.delete(cartKey);
        }

        //删除数据库中临时用户的购物车中的商品数据
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userTempId));
    }


    /**
     * 根据用户id获取该用户的购物车数据 用户可以是登录用户 也可以是临时用户
     * @param userId
     * @return
     */
    private List<CartInfo> getCartList(String userId) {
        //1、创建根据id最终返回的购物车集合 （集合就是购物车，集合中的每一个元素就是该用户的商品）
        List<CartInfo> cartInfoList  = new ArrayList<>();

        //若2、id为空 则直接返回空集合
        if(StringUtils.isEmpty(userId)){
            return cartInfoList;
        }

        //3、根据用户Id构建key 先查询缓存，缓存没有，再查询数据库
        String cartKey = getCartKey(userId);
        //4、获取保存在缓存中该用户的所有要购买的商品信息 即购物车 map保存map的value是每种商品信息 所以最后得到的是list
        cartInfoList = redisTemplate.opsForHash().values(cartKey);

        //5、判断从缓存中是否得到了数据
        if(!CollectionUtils.isEmpty(cartInfoList)){
            // 5.1不为空则得到购物车数据  购物车列表显示有顺序：按照商品的更新时间 降序 现在用id模拟
            cartInfoList.sort((o1,o2)->{
                return o1.getId().compareTo(o2.getId());
            });
            return cartInfoList;

        }else{
            // 5.2缓存没有数据，根据用户Id去数据库查询数据，并放入缓存。
            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }
    }

    /**
     * 根据用户Id 查询数据库中的购物车数据，并添加到缓存！   根据用户Id查询该用户购物车中商品的最新数据并放入缓存
     * 只要是去数据库中获取购物车信息的 说明缓存中的数据都不可用了，所以获取后都要将数据存放到缓存中 进行覆盖更新
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> loadCartCache(String userId) {
        //1、创建根据id最终返回的购物车集合 （集合就是购物车，集合中的每一个元素就是该用户的商品）
        List<CartInfo> cartInfoList  = new ArrayList<>();

        //2、根据用户id去数据库中查询数据
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",userId);
        cartInfoList = cartInfoMapper.selectList(wrapper);

        //3、判断数据库中是否有该用户的购物车信息
        if(CollectionUtils.isEmpty(cartInfoList)){
            //如果数据库中该用户的购物车信息为空
            return cartInfoList;

        }
        // 如果数据库中有购物车列表。
        // 循环遍历，将集合中的每个cartInfo 放入缓存！ 不再一个一个放入，声明一个map，一次放入多条数据
        HashMap<String, CartInfo> cartMap = new HashMap<>();

        //遍历集合
        for (CartInfo cartInfo : cartInfoList) {
            //购物车数据保存到缓存前这里有个细节 既然走到这说明缓存中的数据已失效那么有必要再次查询一下购物车中每个商品的实时价格
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));

            // 将购物车中商品数据放入map中。
            cartMap.put(cartInfo.getSkuId().toString(),cartInfo);
        }

        //创建缓存数据的key
        String cartKey = getCartKey(userId);
        //将cartMap保存到缓存中
        redisTemplate.opsForHash().putAll(cartKey,cartMap);
        //设置缓存过期时间
        setCartKeyExpire(cartKey);

        // 返回最终的数据。
        return cartInfoList;
    }

    /**
     * 更新选中状态 那个用户（登录或未登录）的哪个商品的选中状态
     *
     * @param userId
     * @param isChecked
     * @param skuId
     */
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // update cartInfo set isChecked=? where  skuId = ? and userId=？
        // 先修改数据库
        // 修改wapper第一个参数表示修改的数据，第二个参数表示条件
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);//设置修改项的值（即更新的内容）

        //创建更新条件
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("sku_id", skuId);
        cartInfoMapper.update(cartInfo, queryWrapper);

        //再修改缓存中该sku的选中状态
        // 获取缓存key key user:userId:cart
        String cartKey = this.getCartKey(userId);

        //根据hash数据结构来获取数据 boundHashOps 绑定哈希操作 由此获取到的数据 操作它就相当于操作缓存中的数据
        BoundHashOperations<String, String, CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);

        //判断选中的商品在购物车中是否存在
        if(boundHashOperations.hasKey(skuId.toString())){
            CartInfo cartInfoupd = (CartInfo) boundHashOperations.get(skuId.toString());

            //设置用户选中状态 写入缓存
            cartInfoupd.setIsChecked(isChecked);

            //将修改后的数据放入缓存 更新缓存
            boundHashOperations.put(skuId.toString(),cartInfoupd);

            // 设置过期时间
            setCartKeyExpire(cartKey);

        }


    }

    /**
     * 根据skuid删除购物车中的某项商品 （登录和未登录应该都可以删除）
     * @param userId
     * @param skuId
     * @return
     */
    @Override
    public void deleteCart(Long skuId, String userId) {
        //获取缓存中购物车key
        String cartKey = getCartKey(userId);
        //获取缓存对象  根据hash数据结构来获取数据
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        //判断缓存中购物车是否有该商品
        if (hashOperations.hasKey(skuId.toString())){
            //删除缓存中该用户的购物车中的该sku的数据
            hashOperations.delete(skuId.toString());
        }

        //删除数据库中的该用户的购物车数据
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id", userId).eq("sku_id", skuId));

    }

    /**
     * 根据用户Id 查询购物车列表中被选中的商品
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        //在展示购物车列表后，才会去点击结算，出现订单页面，所以缓存中一定有购物车数据
        //创建最终返回的选中商品列表
        List<CartInfo> cartInfoList = new ArrayList<>();

        // 定义缓存key user:userId:cart
        String cartKey = getCartKey(userId);
        //缓存中查询该用户的购物车数据
        List<CartInfo> cartCachInfoList = redisTemplate.opsForHash().values(cartKey);

        //判断购物车中是否有数据
        if(null!=cartCachInfoList && cartCachInfoList.size()>0){
            for (CartInfo cartInfo : cartCachInfoList) {
                if(cartInfo.getIsChecked().intValue()==1){
                    cartInfoList.add(cartInfo);
                }
            }
        }

        return cartInfoList;
    }

}
