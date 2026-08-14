package com.hq.goods.lang.config;

import com.hq.goods.lang.config.FreeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author weiwh
 * @date 2020/6/13 23:16
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry){

        //放行路径
        InterceptorRegistration freeInter = registry.addInterceptor(new FreeInterceptor());
        freeInter.addPathPatterns("/**", "/test/**", "/signin/**");

    }

}
