package com.hq.goods.lang.bean.dto;

import lombok.Data;

@Data
public class AiMessageReq {
    private String role;
    private String content;

    public AiMessageReq() {

    }
    public AiMessageReq(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
