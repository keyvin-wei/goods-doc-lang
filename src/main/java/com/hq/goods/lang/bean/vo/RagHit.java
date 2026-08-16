package com.hq.goods.lang.bean.vo;

import lombok.Data;

/**
 * RAG 召回命中项
 */
@Data
public class RagHit {
    private String partNumber;
    private String brand;
    private Integer score;

    public RagHit() {
    }

    public RagHit(String partNumber, String brand, Integer score) {
        this.partNumber = partNumber;
        this.brand = brand;
        this.score = score;
    }
}
