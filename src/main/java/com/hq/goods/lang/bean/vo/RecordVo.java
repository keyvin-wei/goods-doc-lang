package com.hq.goods.lang.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cTime;
}
