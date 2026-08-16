package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史列表行
 */
@Data
public class RecordVo {
    private Long id;
    private String partNumber;
    private String brand;
    private String category;
    private String packageType;
    private LocalDateTime cTime;
}
