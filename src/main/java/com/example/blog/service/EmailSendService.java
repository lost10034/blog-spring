package com.example.blog.service;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.dm20151123.models.SingleSendMailResponse;
import com.aliyun.dm20151123.models.SingleSendMailResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import com.example.blog.adapter.AliYunAdapter;
import com.example.blog.adapter.LimitAdapter;
import com.example.blog.adapter.SendCloudAdapter;
import com.example.blog.config.resilience4j.EmailResilience4jConfig;
import com.example.blog.config.sendEmail.SendAliYunDefault;
import com.example.blog.config.sendEmail.SendCloudProperties;
import com.example.blog.enums.SendStatus;
import com.example.blog.enums.StatusLimit;
import com.example.blog.interfaces.RedisLimit;
import com.example.blog.interfaces.SendEmail;
import com.sendcloud.sdk.builder.SendCloudBuilder;
import com.sendcloud.sdk.config.Region;
import com.sendcloud.sdk.core.SendCloud;
import com.sendcloud.sdk.model.MailAddressReceiver;
import com.sendcloud.sdk.model.MailBody;
import com.sendcloud.sdk.model.SendCloudMail;
import com.sendcloud.sdk.model.TextContent;
import com.sendcloud.sdk.util.ResponseData;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
@Service
public class EmailSendService implements SendEmail {

    private static final Logger logger = LoggerFactory.getLogger(EmailSendService.class);
    /** SendCloud 的 API_USER / API_KEY，从环境变量读取，避免把密钥写进仓库 */
    private  final SendCloudProperties sendCloudProperties;
    private  final SendAliYunDefault sendAliYunDefault;
    private  final RedisLimit redisLimit;
    private  final EmailResilience4jConfig resilience4jConfig;
    
    public  EmailSendService(SendCloudProperties sendCloudProperties,SendAliYunDefault sendAliYunDefault,RedisLimit redisLimit,EmailResilience4jConfig resilience4jConfig){
        this.sendCloudProperties=sendCloudProperties;
        this.sendAliYunDefault=sendAliYunDefault;
        this.redisLimit=redisLimit;
        this.resilience4jConfig=resilience4jConfig;
    }
    @Override
    @Async ("sendEmailExecutor")
    public CompletableFuture<SendStatus> SendEmailCaptchaSendCloud(SendCloudAdapter adapter) throws Exception{
         CompletableFuture<SendStatus> SUCCESS=CompletableFuture.completedFuture(SendStatus.SEND_SUCCESS);
        CompletableFuture<SendStatus> FAULT=CompletableFuture.completedFuture(SendStatus.SEND_FAULT);
        StatusLimit limit = redisLimit.expiredLimit(LimitAdapter.builder().key(adapter.address()).value(adapter.limitValue()).timeOut(5).destory(60).build());
        if (limit != StatusLimit.SUCCESS) {
            logger.info("该邮箱处于冷却期间: {}", adapter.address());
            return FAULT;
        }
        MailAddressReceiver receiver = new MailAddressReceiver();
        receiver.addTo(adapter.address());
        MailBody body = new MailBody();
        body.setFrom(adapter.sendAddres());
        body.setFromName(adapter.formName());
        body.setSubject(adapter.subject());
        TextContent content = new TextContent();
        content.setContent_type(adapter.conteType());
        content.setText(adapter.template());
        SendCloudMail email = new SendCloudMail();
        email.setTo(receiver);
        email.setBody(body);
        email.setContent(content);
        SendCloud sendCloud = SendCloudBuilder.build();
        try {
            ResponseData response =this.resilience4jConfig.basicDefault( () -> sendCloud.sendMail(
                email, sendCloudProperties.credential(), Region.CN, null, null, null),
        "sendCloud").get();
            logger.info("SendCloud 响应: result={}, statusCode={}, message={}, info={}",
                    response.getResult(), response.getStatusCode(), response.getMessage(), response.getInfo());
            return response.getResult() ? SUCCESS : FAULT;
        } catch (CallNotPermittedException e) {
    logger.warn("SendCloud 熔断器已打开，拒绝发送");
    return FAULT;
} catch (Throwable e) {
    if (e instanceof Error error) {
        throw error;
    }
    logger.error("SendCloud 邮件发送异常", e);
    return FAULT;
}
    }
    @Override 
    @Async ("sendEmailExecutor")
    public  CompletableFuture<SendStatus> SendEmailCaptchaAliYun(AliYunAdapter aliYunAdapter)throws Exception{
        CompletableFuture<SendStatus> SUCCESS=CompletableFuture.completedFuture(SendStatus.SEND_SUCCESS);
        CompletableFuture<SendStatus> FAULT=CompletableFuture.completedFuture(SendStatus.SEND_FAULT);
        StatusLimit limit = redisLimit.expiredLimit(LimitAdapter.builder().key(aliYunAdapter.toAddress()).value(aliYunAdapter.limitValue()).timeOut(5).destory(60).build());
        if (limit != StatusLimit.SUCCESS) {
            logger.info("该邮箱处于冷却期间: {}", aliYunAdapter.toAddress());
            return FAULT;
        }
       boolean hasReplyAddress = aliYunAdapter.ReplyAddress() != null
               && !aliYunAdapter.ReplyAddress().isBlank();
       SingleSendMailRequest request=new SingleSendMailRequest()
               .setAccountName(aliYunAdapter.accountName())
               .setAddressType(aliYunAdapter.accountType())
               .setReplyToAddress(hasReplyAddress)
               .setToAddress(aliYunAdapter.toAddress())
               .setFromAlias(aliYunAdapter.forName())
               .setSubject(aliYunAdapter.subject())
               .setReplyAddress(aliYunAdapter.ReplyAddress())
               .setHtmlBody(aliYunAdapter.template());
       if (hasReplyAddress) {
           request.setReplyAddress(aliYunAdapter.ReplyAddress());
       }
       try{
        SingleSendMailResponse response = resilience4jConfig.basicDefault(() -> sendAliYunDefault.dmClient()
                        .singleSendMailWithOptions(request, new RuntimeOptions()), "aliyun").get();
        SingleSendMailResponseBody body=response.getBody();
        logger.info("阿里云 响应：statusCode={},requestId={},envId={}", response.getStatusCode(),body.getRequestId(),body.getEnvId());
        return  response.getStatusCode()!=null&&response.getStatusCode()/100==2?SUCCESS:FAULT;
       } catch (CallNotPermittedException e) {
        logger.warn("阿里云熔断器已打开，拒绝发送");
        return FAULT;
       } catch (Throwable e) {
        if (e instanceof Error error) {
            throw error;
        }
        if (e instanceof TeaException teaException) {
            logger.error("阿里云邮箱发送失败：{} 诊断：{}",
                    teaException.getMessage(),
                    teaException.getData() == null ? null : teaException.getData().get("Recommend"));
        } else {
            logger.error("阿里云邮箱发送异常", e);
        }
        return FAULT;
       }
        
    }
   
}
