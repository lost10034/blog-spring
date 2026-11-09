package com.example.blog.service.captchaSend;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

import com.example.blog.interfaces.Captcha;

@Service
public class CaptchaService implements Captcha {
    private final SecureRandom random;

    public CaptchaService(SecureRandom random) {
        this.random = random;
    }

    @Override
    public int emailCaptcha() {
        return 100_000 + random.nextInt(900_000);
    }
}
