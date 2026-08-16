package com.hq.goods.lang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.vo.RagHit;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import com.hq.goods.lang.service.RagService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简单实现：从 hq_goods_doc_record 按 型号/品牌/分类/封装 子串匹配计分，取 TOP-K。
 */
@Service
public class RagServiceImpl implements RagService {

    @Autowired
    private GoodsDocRecordDao goodsDocRecordDao;

    @Override
    public List<RagHit> searchTopK(String query, int k) {
        if (StringUtils.isBlank(query) || k <= 0) {
            return Collections.emptyList();
        }
        List<GoodsDocRecord> records = goodsDocRecordDao.selectList(
                new QueryWrapper<GoodsDocRecord>().eq("delete_status", 0));
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(r -> new RagHit(r.getPartNumber(), r.getBrand(), score(r, query)))
                .filter(h -> h.getScore() != null && h.getScore() > 0)
                .sorted(Comparator.comparingInt(RagHit::getScore).reversed())
                .limit(k)
                .collect(Collectors.toList());
    }

    /**
     * 简单计分：型号4 / 品牌3 / 分类2 / 封装1（双向子串匹配）
     */
    static int score(GoodsDocRecord r, String query) {
        if (r == null || StringUtils.isBlank(query)) {
            return 0;
        }
        String q = query.toLowerCase();
        int s = 0;
        if (hit(r.getPartNumber(), q)) {
            s += 4;
        }
        if (hit(r.getBrand(), q)) {
            s += 3;
        }
        if (hit(r.getCategory(), q)) {
            s += 2;
        }
        if (hit(r.getPackageType(), q)) {
            s += 1;
        }
        return s;
    }

    private static boolean hit(String field, String q) {
        if (StringUtils.isBlank(field)) {
            return false;
        }
        String f = field.toLowerCase();
        return q.contains(f) || f.contains(q);
    }
}
