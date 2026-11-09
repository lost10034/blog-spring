package com.example.blog.emailSend;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.example.blog.adapter.SendCloudAdapter;
import com.example.blog.config.sendEmail.SendAliYunDefault;
import com.example.blog.config.sendEmail.SendCloudProperties;
import com.example.blog.enums.SendStatus;
import com.example.blog.service.EmailSendService;
import com.example.blog.service.TemplateService;
import com.sendcloud.sdk.model.TextContent.ScContentType;

/**
 * 真发邮件的联调测试，默认不执行（避免每次跑测试都对外发信）。
 * 只有显式设置以下环境变量时才运行：
 *
 * <pre>
 * SENDCLOUD_LIVE=true \
 * SENDCLOUD_API_USER=xxx SENDCLOUD_API_KEY=yyy \
 * ./gradlew test --tests 'com.example.blog.emailSend.EmailSendLiveTest' -i --rerun
 *
 * 注意必须带 --rerun：环境变量不在 test 任务输入里，代码没变时 Gradle 会 UP-TO-DATE 直接复用上次结果，
 * 看起来"没发信"其实是压根没执行。
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SENDCLOUD_LIVE", matches = "true")
class EmailSendLiveTest {

    private static final String TO = "1541089174@qq.com";
    private static final String FROM = "no-reply@buymvi.sendcloud.org";
    private static final String FROM_NAME = "博客验证码";
    private static final String SUBJECT = "邮箱验证码";

    @Test
    void sendsRealEmail() throws Exception {
        EmailSendService service = new EmailSendService(sendCloudProperties(), new SendAliYunDefault());

        SendCloudAdapter adapter = new SendCloudAdapter(
                TO,                 // address: 收件人
                FROM,               // sendAddres: 发件地址
                renderEmail(),      // template: 渲染好的 HTML 正文
                FROM_NAME,          // formName: 发件人显示名
                SUBJECT,            // subject: 邮件主题
                ScContentType.html, // conteType: 正文类型
                null                // text: 纯文本内容（本服务未使用）
        );

        SendStatus status = service.SendEmailCaptchaSendCloud(adapter).get(60, TimeUnit.SECONDS);

        assertThat(status).isEqualTo(SendStatus.SEND_SUCCESS);
    }

    /**
     * SendCloudProperties 的构造器是 private 的，设计上由 Spring 进行构造器绑定创建；
     * 联调测试不启动 Spring 上下文，这里直接用环境变量反射构造一个等价实例。
     */
    private SendCloudProperties sendCloudProperties() throws Exception {
        String apiUser = System.getenv("SENDCLOUD_API_USER");
        String apiKey = System.getenv("SENDCLOUD_API_KEY");

        Constructor<SendCloudProperties> constructor =
                SendCloudProperties.class.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(apiUser, apiKey);

    }

    private String renderEmail() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        return new TemplateService(templateEngine).renderEmail(5, "卡缪", 123456);
    }
}
