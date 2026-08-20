package com.hq.goods.lang.service;

import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.bean.vo.GoodsDocDescVo;
import com.hq.goods.lang.bean.vo.GoodsDocMultiVo;
import com.hq.goods.lang.bean.vo.GoodsDocRecordVo;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.PageResult;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.bean.vo.RecordVo;

/**
 * 外贸商品文档编排服务
 */
public interface GoodsDocService {

    /** 型号+品牌解析 */
    GoodsDocVo parsePart(ParsePartReq req);

    /** 描述文本解析 */
    GoodsDocVo parseText(ParseTextReq req);

    /** 生成英文标准描述 */
    GoodsDocDescVo generateDesc(GenerateDescReq req);

    /** 生成多语言 + SEO */
    GoodsDocMultiVo generateMulti(GenerateMultiReq req);

    /** 保存（无 id 新增 / 有 id 更新） */
    Long save(SaveReq req);

    /** 历史分页列表（按型号精确搜索，partNumber 可空） */
    PageResult<RecordVo> list(int page, int size, String partNumber);

    /** 后台详情 */
    GoodsDocRecordVo detail(Long id);

    /** 逻辑删除 */
    void delete(Long id);

    /** 客户页面公开数据 */
    ProductVo product(Long id);
}
