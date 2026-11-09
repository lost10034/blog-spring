package com.example.blog.emailSend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.example.blog.service.TemplateService;

/**
 * 纯单元测试：不启动 Spring 上下文，不依赖 MySQL / Redis，也不会真的发邮件。
 * 模板引擎手动构造，直接验证 src/main/resources/templates/email.html 的渲染结果。
 */
class EmailSendTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        templateService = new TemplateService(templateEngine);
    }

    @Test
    void rendersEmailTemplate() {
        String html = templateService.renderEmail(5, "卡缪", 123456);

        assertThat(html)
                .contains("你好，卡缪")
                .contains("你的的验证码是：123456")
                .contains("验证码将在：5 分钟过期")
                .doesNotContain("th:text");
    }
}
