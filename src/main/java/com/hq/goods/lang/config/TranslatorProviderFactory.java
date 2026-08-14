package com.hq.goods.lang.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译提供商工厂
 *
 * <p>根据配置动态选择翻译提供商实现，支持配置化切换。</p>
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@Slf4j
@Component
public class TranslatorProviderFactory {

    @Value("${hq.nextpcb.ai.translation.provider:aihubmix}")
    private String providerName;

    @Autowired
    private List<TranslatorProvider> providers;

    private final Map<String, TranslatorProvider> providerMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (providers != null) {
            for (TranslatorProvider provider : providers) {
                providerMap.put(provider.getProviderName(), provider);
                log.info("[TranslatorProviderFactory] 注册翻译提供商: {}", provider.getProviderName());
            }
        }
        TranslatorProvider defaultProvider = getProvider();
        log.info("[TranslatorProviderFactory] 当前默认提供商: {}({})",
                providerName, defaultProvider.getClass().getSimpleName());
    }

    /**
     * 获取当前配置的翻译提供商
     */
    public TranslatorProvider getProvider() {
        return getProvider(providerName);
    }

    /**
     * 根据名称获取翻译提供商
     *
     * @param name 提供商名称，如 aihubmix、deepseek
     * @return 对应的提供商实现
     */
    public TranslatorProvider getProvider(String name) {
        if (name == null || name.isEmpty()) {
            name = "aihubmix";
        }
        TranslatorProvider provider = providerMap.get(name);
        if (provider == null) {
            log.error("[TranslatorProviderFactory] 未找到翻译提供商: {}，已注册: {}",
                    name, providerMap.keySet());
            throw new IllegalStateException("未找到翻译提供商: " + name);
        }
        return provider;
    }

    /**
     * 切换当前默认提供商（运行时动态切换）
     *
     * @param name 提供商名称
     */
    public void switchProvider(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("provider name cannot be empty");
        }
        if (!providerMap.containsKey(name)) {
            throw new IllegalArgumentException("unknown provider: " + name + ", available: " + providerMap.keySet());
        }
        this.providerName = name;
        log.info("[TranslatorProviderFactory] 切换默认提供商为: {}", name);
    }
}
