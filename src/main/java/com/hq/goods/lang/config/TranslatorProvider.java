package com.hq.goods.lang.config;

/**
 * 翻译提供商接口
 *
 * <p>抽象不同AI翻译后端（AiHubMix、DeepSeek、OpenAI等），便于未来切换或扩展。</p>
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
public interface TranslatorProvider {

    /**
     * 执行翻译请求
     *
     * @param model        模型名称，如 gpt-5.2、deepseek-chat
     * @param systemPrompt System Prompt
     * @param userPrompt   User Prompt
     * @return AI翻译结果文本
     */
    String translate(String model, String systemPrompt, String userPrompt);

    /**
     * 获取提供商名称
     *
     * @return 名称标识，如 "aihubmix"、"deepseek"
     */
    String getProviderName();
}
