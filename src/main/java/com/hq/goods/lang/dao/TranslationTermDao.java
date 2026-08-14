package com.hq.goods.lang.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hq.goods.lang.bean.entity.TranslationTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PCB术语库 Mapper 接口
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@Mapper
@Repository
public interface TranslationTermDao extends BaseMapper<TranslationTerm> {

    /**
     * 根据分类查询术语列表
     *
     * @param category 分类
     * @return 术语列表
     */
    List<TranslationTerm> selectByCategory(@Param("category") String category);

    /**
     * 根据中文术语模糊查询
     *
     * @param cn 中文术语关键字
     * @return 术语列表
     */
    List<TranslationTerm> selectByCnLike(@Param("cn") String cn);

    /**
     * 查询所有有效术语(用于初始化缓存或导出)
     *
     * @return 术语列表
     */
    List<TranslationTerm> selectAllValid();

    /**
     * 原子递增使用计数
     *
     * @param termIds 术语ID
     * @return 影响行数
     */
    int incrementUsageCount(@Param("termIds") List<Long> termIds);
}
