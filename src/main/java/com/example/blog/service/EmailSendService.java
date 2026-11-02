package com.example.blog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.blog.enums.SendStatus;
import com.example.blog.interfaces.SendEmail;
import com.sendcloud.sdk.builder.SendCloudBuilder;
import com.sendcloud.sdk.core.SendCloud;
import com.sendcloud.sdk.model.MailAddressReceiver;
import com.sendcloud.sdk.model.MailBody;
import com.sendcloud.sdk.model.SendCloudMail;
import com.sendcloud.sdk.model.TextContent;
import com.sendcloud.sdk.model.TextContent.ScContentType;
@Service
public class EmailSendService implements SendEmail {
    private static final Logger logger = LoggerFactory.getLogger(EmailSendService.class);

    @Override
    public  SendStatus SendEmailCaptcha(String address,String sendAddress,String template){
        MailAddressReceiver receiver=new  MailAddressReceiver();
         receiver.addTo(sendAddress);
        MailBody body=new  MailBody();
        body.setFrom(address);
       body.setFromName("验证码");
       body.setSubject("邮箱验证码");
        TextContent content = new TextContent();  
        content.setContent_type(ScContentType.html);  
        content.setText(template);
        SendCloudMail email=new  SendCloudMail();
        email.setTo(receiver);
        email.setBody(body);
        email.setContent(content);
        SendCloud sendCloud=SendCloudBuilder.build();
        try {
    var response = sendCloud.sendMail(email);
    if (response.getResult()) {
    return SendStatus.SEND_SUCCESS;
}
return SendStatus.SEND_FAULT;
} catch (Exception e) {
      logger.error("SendCloud 邮件发送异常", e);
    return SendStatus.SEND_FAULT;
}
       
    }
}
