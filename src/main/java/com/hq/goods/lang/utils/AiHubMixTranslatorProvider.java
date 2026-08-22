package com.hq.goods.lang.utils;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.hq.goods.lang.bean.AddressConstant;
import com.hq.goods.lang.bean.dto.AiMessageReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AiHubMix翻译提供商实现
 *
 * <p>基于gpt-5.5模型，通过HttpUtil.postAihubmix发送请求。</p>
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@Slf4j
@Component
public class AiHubMixTranslatorProvider implements TranslatorProvider {

    @Override
    public String translate(String model, String systemPrompt, String userPrompt) {
        JSONObject params = new JSONObject();
        params.put("model", model);
        params.put("max_completion_tokens", 3000);
        params.put("temperature", 0.1);
        // params.put("top_p", 0.2);
        params.put("stream", false);

        List<AiMessageReq> messages = new ArrayList<>();
        messages.add(new AiMessageReq("system", systemPrompt));
        messages.add(new AiMessageReq("user", userPrompt));
        params.put("messages", messages);

        String bodyParam = JSONObject.toJSONString(params, SerializerFeature.WriteMapNullValue);
        log.info("[AiHubMixProvider] 请求model={}，入参：{}", model, bodyParam);
        return HttpUtil.postAihubmixContent(HttpUtil.aihubmixUrl + AddressConstant.AIHUBMIX_CHAT_COMPLETIONS, bodyParam);
    }

    @Override
    public String getProviderName() {
        return "aihubmix";
    }
}
