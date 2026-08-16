package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果
 */
@Data
public class PageResult<T> {
    private long total;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }
}
