package com.hq.goods.lang.controller;

import com.hq.goods.lang.bean.ResultBody;
import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.service.GoodsDocService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 外贸商品文档接口
 */
@RestController
@RequestMapping("/api/doc")
public class GoodsDocController {

    @Autowired
    private GoodsDocService goodsDocService;

    /** ① 型号+品牌解析 */
    @PostMapping("/parsePart")
    public String parsePart(@RequestBody @Valid ParsePartReq req) {
        return ResultBody.success(goodsDocService.parsePart(req));
    }

    /** ② 描述文本解析 */
    @PostMapping("/parseText")
    public String parseText(@RequestBody @Valid ParseTextReq req) {
        return ResultBody.success(goodsDocService.parseText(req));
    }

    /** ③ 英文标准描述生成 */
    @PostMapping("/generateDesc")
    public String generateDesc(@RequestBody @Valid GenerateDescReq req) {
        return ResultBody.success(goodsDocService.generateDesc(req));
    }

    /** ④ 多语言 + SEO 生成 */
    @PostMapping("/generateMulti")
    public String generateMulti(@RequestBody @Valid GenerateMultiReq req) {
        return ResultBody.success(goodsDocService.generateMulti(req));
    }

    /** ⑤ 保存（新增/更新） */
    @PostMapping("/save")
    public String save(@RequestBody @Valid SaveReq req) {
        return ResultBody.success(goodsDocService.save(req));
    }

    /** ⑥ 历史分页列表（按型号精确搜索，partNumber 可空） */
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String partNumber) {
        return ResultBody.success(goodsDocService.list(page, size, partNumber));
    }

    /** ⑦ 后台详情 */
    @GetMapping("/detail")
    public String detail(@RequestParam Long id) {
        return ResultBody.success(goodsDocService.detail(id));
    }

    /** ⑧ 逻辑删除 */
    @DeleteMapping("/delete")
    public String delete(@RequestParam Long id) {
        goodsDocService.delete(id);
        return ResultBody.success();
    }

    /** ⑨ 客户页面公开数据（官网 SSR；多语言按 cookie lang） */
    @GetMapping("/product/{id}")
    public String product(@PathVariable Long id, HttpServletRequest request) {
        return ResultBody.success(goodsDocService.product(id, request));
    }
}
