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
import java.util.LinkedHashMap;
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
     * 校验 LLM 原始输出提取后是合法 JSON，非法则抛 CustomException（供服务层重试判定）
     */
    public static void validateJson(String raw) {
        String json = extractJson(raw);
        if (!isValidJson(json)) {
            throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 输出未包含有效 JSON");
        }
    }

    /**
     * 判断给定字符串是否为合法 JSON
     */
    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        try {
            return JSON.parse(json) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * LLM 解析结果 JSON → GoodsDocVo（容错：字段缺失/类型偏离不报错，解析失败返回空 VO）
     */
    public static GoodsDocVo toGoodsDocVo(String json) {
        GoodsDocVo vo = new GoodsDocVo();
        if (StringUtils.isBlank(json)) {
            return vo;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            return vo;
        }
        if (obj == null) {
            return vo;
        }
        vo.setPartNumber(obj.getString("partNumber"));
        vo.setBrand(obj.getString("brand"));
        vo.setCategory(obj.getString("category"));
        vo.setSubcategory(obj.getString("subcategory"));
        vo.setSeries(obj.getString("series"));
        vo.setPackageType(obj.getString("packageType"));
        vo.setMountingType(obj.getString("mountingType"));
        vo.setPinCount(toIntSafe(obj.get("pinCount")));
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

        Object paramsRaw = obj.get("parameters");
        if (paramsRaw instanceof JSONArray) {
            JSONArray params = (JSONArray) paramsRaw;
            List<ParamItem> list = new ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                Object po = params.get(i);
                if (!(po instanceof JSONObject)) {
                    continue;
                }
                JSONObject p = (JSONObject) po;
                ParamItem item = new ParamItem();
                item.setName(p.getString("name"));
                item.setValue(p.getString("value"));
                item.setUnit(p.getString("unit"));
                list.add(item);
            }
            if (!list.isEmpty()) {
                vo.setParameters(list);
            }
        }

        Object appsRaw = obj.get("applications");
        if (appsRaw instanceof JSONArray) {
            JSONArray apps = (JSONArray) appsRaw;
            List<String> appList = new ArrayList<>();
            for (int i = 0; i < apps.size(); i++) {
                Object a = apps.get(i);
                if (a instanceof String && StringUtils.isNotBlank((String) a)) {
                    appList.add((String) a);
                }
            }
            vo.setApplications(appList);
        }
        return vo;
    }

    /**
     * SEO JSON → Map<语言码, SeoVo>（zh/en/ja/ru，容错：解析失败返回空 Map）
     */
    public static Map<String, SeoVo> parseSeo(String json) {
        Map<String, SeoVo> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            return result;
        }
        if (obj == null) {
            return result;
        }
        for (String key : obj.keySet()) {
            Object so = obj.get(key);
            if (!(so instanceof JSONObject)) {
                continue;
            }
            JSONObject s = (JSONObject) so;
            SeoVo seo = new SeoVo();
            seo.setTitle(s.getString("title"));
            seo.setDescription(s.getString("description"));
            Object kwRaw = s.get("keywords");
            if (kwRaw instanceof JSONArray) {
                JSONArray kw = (JSONArray) kwRaw;
                List<String> keywords = new ArrayList<>();
                for (int i = 0; i < kw.size(); i++) {
                    Object k = kw.get(i);
                    if (k instanceof String && StringUtils.isNotBlank((String) k)) {
                        keywords.add((String) k);
                    }
                }
                seo.setKeywords(keywords);
            }
            result.put(key, seo);
        }
        return result;
    }

    /**
     * 安全转 int：null/非数字返回 null，数字字符串/数值直接解析
     */
    private static Integer toIntSafe(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = String.valueOf(o).trim();
        if (StringUtils.isBlank(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
