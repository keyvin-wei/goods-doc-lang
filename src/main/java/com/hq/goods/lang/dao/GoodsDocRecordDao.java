package com.hq.goods.lang.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * 外贸商品文档记录 Mapper
 */
@Mapper
@Repository
public interface GoodsDocRecordDao extends BaseMapper<GoodsDocRecord> {
}
