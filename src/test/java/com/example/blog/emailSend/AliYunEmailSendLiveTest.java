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
 * Sends a real email only when ALIYUN_LIVE=true is explicitly set.
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
                "<p>这是一封阿里云邮件发送测试。</p>",
                accountName,
                addressType,
                replyAddress,
                recipient,
                fromAlias,
                "阿里云邮件发送测试"
                );

        EmailSendService service = new EmailSendService(null, new SendAliYunDefault());
        SendStatus status = service.SendEmailCaptchaAliYun(adapter).get(60, TimeUnit.SECONDS);

        assertThat(status).isEqualTo(SendStatus.SEND_SUCCESS);
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        assertThat(value).as("Environment variable %s must be set", name).isNotBlank();
        return value;
    }
}
