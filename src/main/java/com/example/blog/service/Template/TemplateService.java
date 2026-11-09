package com.example.blog.service.Template;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import com.example.blog.interfaces.Template;

import org.thymeleaf.context.Context;
@Service 
public class TemplateService implements Template {
    private  final TemplateEngine templateEngine;
   public  TemplateService(TemplateEngine templateEngine){
        this.templateEngine=templateEngine;
        
    }
    @Override 
    public  String renderEmail(int survival,String name,int captcha){
        Context context=new Context();
            context.setVariable("userName",name);
            context.setVariable("captcha", captcha);
            context.setVariable("time", survival);
            return  templateEngine.process("email", context);
            
        
    }
    
}
