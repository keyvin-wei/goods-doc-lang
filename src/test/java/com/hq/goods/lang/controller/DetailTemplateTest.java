package com.hq.goods.lang.controller;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * 模板冒烟测试：验证 detail.html / not-found.html 存在且含关键标记。
 * 实际渲染效果由人工在浏览器验证。
 */
public class DetailTemplateTest {

    @Test
    public void testDetailTemplateContainsRequiredMarkers() throws Exception {
        File f = new File("src/main/resources/templates/detail.html");
        assertTrue("detail.html 不存在", f.exists());
        String html = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue("缺少 JSON-LD script", html.contains("application/ld+json"));
        assertTrue("缺少语言切换", html.contains("langSelect"));
        assertTrue("缺少 canonical", html.contains("rel=\"canonical\""));
        assertTrue("缺少 hreflang x-default", html.contains("x-default"));
        assertTrue("缺少 demo1 图", html.contains("demo1.png"));
        assertTrue("缺少 demo2 图", html.contains("demo2.png"));
        assertTrue("缺少 demo3 图", html.contains("demo3.png"));
        assertTrue("缺少阶梯价渲染", html.contains("vo.prices"));
        assertTrue("缺少参数表渲染", html.contains("vo.parameters"));
    }

    @Test
    public void testNotFoundTemplateExists() {
        assertTrue("not-found.html 不存在",
                new File("src/main/resources/templates/not-found.html").exists());
    }
}
