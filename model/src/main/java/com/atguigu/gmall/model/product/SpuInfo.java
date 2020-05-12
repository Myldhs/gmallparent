package com.atguigu.gmall.model.product;

import com.atguigu.gmall.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * SpuInfo
 * </p>
 *
 */
@Data
@ApiModel(description = "SpuInfo")
@TableName("spu_info")
public class SpuInfo extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	
	@ApiModelProperty(value = "商品名称")
	@TableField("spu_name")
	private String spuName;

	@ApiModelProperty(value = "商品描述(后台简述）")
	@TableField("description")
	private String description;

	@ApiModelProperty(value = "三级分类id")
	@TableField("category3_id")
	private Long category3Id;

	@ApiModelProperty(value = "品牌id")
	@TableField("tm_id")
	private Long tmId;

	// spu商品销售属性集合 每一个商品销售属性对象中都有一个商品销售属性值集合属性（与前端进行数据交互的）
	@TableField(exist = false)
	private List<SpuSaleAttr> spuSaleAttrList;

	//spu商品图片集合（与前端进行数据交互的）
	@TableField(exist = false)
	private List<SpuImage> spuImageList;

}

