package com.hq.goods.lang.service;

import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.vo.AiTranslateVo;

/**
 * @Description 对接ai相关接口，对接aihubmix
 * @Author weiwenhan
 * @Date 2026/8/14 17:53
 */
public interface AiLLMService {
    /**
     * AI翻译接口
     * @param dto
     * @return
     */
    AiTranslateVo aiTranslate(AiTranslateDto dto);

}
