package com.example.blog.emailSend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.example.blog.adapter.AliYunAdapter;
import com.example.blog.config.sendEmail.SendAliYunDefault;
import com.example.blog.enums.SendStatus;
import com.example.blog.service.EmailSendService;

/**
 * 真发邮件的联调测试，默认不执行（避免每次跑测试都对外发信）。
 * 只有显式设置以下环境变量时才运行：
 *
 * <pre>
 * ALIYUN_LIVE=true \
 * ALIYUN_ACCOUNT_NAME=xxx ALIYUN_ADDRESS_TYPE=0 ALIYUN_TEST_TO=yyy \
 * ALIYUN_FROM_ALIAS=zzz \
 * ./gradlew test --tests 'com.example.blog.emailSend.AliYunEmailSendLiveTest' -i --rerun
 *
 * 注意必须带 --rerun：环境变量不在 test 任务输入里，代码没变时 Gradle 会 UP-TO-DATE 直接复用上次结果，
 * 看起来"没发信"其实是压根没执行。
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "ALIYUN_LIVE", matches = "true")
class AliYunEmailSendLiveTest {

    @Test
    void sendsRealEmail() throws Exception {
        String accountName = requiredEnvironmentVariable("ALIYUN_ACCOUNT_NAME");
        int addressType = Integer.parseInt(requiredEnvironmentVariable("ALIYUN_ADDRESS_TYPE"));
        String recipient = requiredEnvironmentVariable("ALIYUN_TEST_TO");
        String fromAlias = requiredEnvironmentVariable("ALIYUN_FROM_ALIAS");
        String replyAddress = System.getenv("ALIYUN_REPLY_ADDRESS");

        AliYunAdapter adapter = new AliYunAdapter(
                "<p>这是一封阿里云邮件发送测试。</p>",  // template: HTML 正文
                accountName,                          // accountName: 发信地址
                addressType,                          // accountType: 地址类型
                replyAddress,                         // ReplyAddress: 回信地址（可空）
                recipient,                            // toAddress: 收件人
                fromAlias,                            // forName: 发件人显示名
                "阿里云邮件发送测试",                  // subject: 邮件主题
                "1"                                   // limitValue: 冷却锁占位值
        );

        EmailSendService service = new EmailSendService(null, new SendAliYunDefault(), new NoOpRedisLimit());
        SendStatus status = service.SendEmailCaptchaAliYun(adapter).get(60, TimeUnit.SECONDS);

        assertThat(status).isEqualTo(SendStatus.SEND_SUCCESS);
    }

    /**
     * 读取必填环境变量；缺失或为空时直接让断言失败，避免带着空参数对外发信。
     */
    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        assertThat(value).as("Environment variable %s must be set", name).isNotBlank();
        return value;
    }
}
