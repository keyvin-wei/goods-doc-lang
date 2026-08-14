package com.hq.goods.lang.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hq.goods.lang.bean.entity.TranslationLog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * 翻译日志 Mapper 接口
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@Mapper
@Repository
public interface TranslationLogDao extends BaseMapper<TranslationLog> {



}
