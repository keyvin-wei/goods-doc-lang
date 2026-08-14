package com.hq.goods.lang.bean.dto;

import lombok.Data;

@Data
public class AiMessageReq {
    private String role;
    private String constent;

    public AiMessageReq() {

    }
    public AiMessageReq(String role, String constent) {
        this.role = role;
        this.constent = constent;
    }
}
