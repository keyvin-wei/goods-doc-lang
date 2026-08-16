package com.hq.goods.lang.service;

import com.hq.goods.lang.bean.vo.RagHit;

import java.util.List;

/**
 * RAG 检索服务。
 * 当前为简单实现（MySQL LIKE 计分）；后续 ES 部署后新增 ES 向量检索实现（相同签名），
 * 通过配置切换 Bean，调用方不变。
 */
public interface RagService {

    /**
     * 相似商品 TOP-K 召回
     *
     * @param query 查询文本
     * @param k     top-k 数量
     */
    List<RagHit> searchTopK(String query, int k);
}
