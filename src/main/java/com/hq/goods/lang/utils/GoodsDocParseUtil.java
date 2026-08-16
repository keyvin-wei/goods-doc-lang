package com.hq.goods.lang.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.ResponseEnum;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.ParamItem;
import com.hq.goods.lang.bean.vo.SeoVo;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 输出 JSON 提取与结构化映射工具
 */
public final class GoodsDocParseUtil {

    private GoodsDocParseUtil() {
    }

    /**
     * 从 LLM 输出中提取 JSON（自动去掉 markdown 代码块围栏与前后杂文本）
     */
    public static String extractJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 输出为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            start = raw.indexOf('[');
            end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) {
                throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 输出未包含有效 JSON");
            }
        }
        return raw.substring(start, end + 1).trim();
    }

    /**
     * LLM 解析结果 JSON → GoodsDocVo（容错：字段缺失不报错）
     */
    public static GoodsDocVo toGoodsDocVo(String json) {
        if (StringUtils.isBlank(json)) {
            return new GoodsDocVo();
        }
        JSONObject obj = JSON.parseObject(json);
        if (obj == null) {
            return new GoodsDocVo();
        }
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber(obj.getString("partNumber"));
        vo.setBrand(obj.getString("brand"));
        vo.setCategory(obj.getString("category"));
        vo.setSubcategory(obj.getString("subcategory"));
        vo.setSeries(obj.getString("series"));
        vo.setPackageType(obj.getString("packageType"));
        vo.setMountingType(obj.getString("mountingType"));
        vo.setPinCount(obj.getInteger("pinCount"));
        vo.setDimensions(obj.getString("dimensions"));
        vo.setOperatingTemp(obj.getString("operatingTemp"));
        vo.setStorageTemp(obj.getString("storageTemp"));
        vo.setGrade(obj.getString("grade"));
        vo.setRohs(obj.getString("rohs"));
        vo.setPackaging(obj.getString("packaging"));
        vo.setMoq(obj.getString("moq"));
        vo.setUnit(obj.getString("unit"));
        vo.setHsCode(obj.getString("hsCode"));
        vo.setLeadTime(obj.getString("leadTime"));
        vo.setPriceRange(obj.getString("priceRange"));
        vo.setAvailability(obj.getString("availability"));
        vo.setDatasheetUrl(obj.getString("datasheetUrl"));
        vo.setImageUrl(obj.getString("imageUrl"));

        JSONArray params = obj.getJSONArray("parameters");
        if (params != null) {
            List<ParamItem> list = new ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                JSONObject p = params.getJSONObject(i);
                if (p == null) {
                    continue;
                }
                ParamItem item = new ParamItem();
                item.setName(p.getString("name"));
                item.setValue(p.getString("value"));
                item.setUnit(p.getString("unit"));
                list.add(item);
            }
            vo.setParameters(list);
        }

        JSONArray apps = obj.getJSONArray("applications");
        if (apps != null) {
            List<String> appList = new ArrayList<>();
            for (int i = 0; i < apps.size(); i++) {
                String a = apps.getString(i);
                if (StringUtils.isNotBlank(a)) {
                    appList.add(a);
                }
            }
            vo.setApplications(appList);
        }
        return vo;
    }

    /**
     * SEO JSON → Map<语言码, SeoVo>（zh/en/ja/ru）
     */
    public static Map<String, SeoVo> parseSeo(String json) {
        Map<String, SeoVo> result = new HashMap<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        JSONObject obj = JSON.parseObject(json);
        if (obj == null) {
            return result;
        }
        for (String key : obj.keySet()) {
            JSONObject s = obj.getJSONObject(key);
            if (s == null) {
                continue;
            }
            SeoVo seo = new SeoVo();
            seo.setTitle(s.getString("title"));
            seo.setDescription(s.getString("description"));
            JSONArray kw = s.getJSONArray("keywords");
            if (kw != null) {
                List<String> keywords = new ArrayList<>();
                for (int i = 0; i < kw.size(); i++) {
                    String k = kw.getString(i);
                    if (StringUtils.isNotBlank(k)) {
                        keywords.add(k);
                    }
                }
                seo.setKeywords(keywords);
            }
            result.put(key, seo);
        }
        return result;
    }
}
