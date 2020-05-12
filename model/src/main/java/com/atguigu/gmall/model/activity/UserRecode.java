package com.atguigu.gmall.model.activity;

import lombok.Data;

import java.io.Serializable;

//抢购用户的实体类 只记录了那个用户秒杀那个商品 放在mq中
@Data
public class UserRecode implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long skuId;
	
	private String userId;
}
