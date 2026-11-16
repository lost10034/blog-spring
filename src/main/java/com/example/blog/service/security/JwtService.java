package com.example.blog.service.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.blog.adapter.JwtAdapter;
import com.example.blog.adapter.LimitAdapter;
import com.example.blog.interfaces.RedisLimit;
@Service 
public class JwtService {
    @Value ("${jwt.secret}")
    private  String secret ;
    private  RedisLimit redisLimit;
    private  final Logger logger=LoggerFactory.getLogger(JwtService.class);
    JwtService(RedisLimit rLimit){
        this.redisLimit=rLimit;
    }
    public SecretKey getkey(){
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public  String jwtCreate(JwtAdapter jwt){
        
        return Jwts.builder().signWith(getkey()).subject(jwt.id()).
        issuedAt(new  Date()).expiration(new Date(System.currentTimeMillis()+jwt.millis()))
       .claims(jwt.claim()).compact();
    }
     public  Claims jwtParse(String token){
        return  Jwts.parser().verifyWith(getkey()).build().parseSignedClaims(token).getPayload();
     }
     public Boolean vaildateToken(String token){
        try {
            jwtParse(token);
            return  true;
        }catch(ExpiredJwtException e){
            logger.info("jwt已经过期无法使用",e);
            return  false;
        } catch (Exception e) {
            return  false;
        }
     }
     public String RefreshToken(JwtAdapter jwt){
        String token=jwtCreate(jwt);
        redisLimit.expiredLimit(LimitAdapter.builder().key(jwt.id()).value(token).destory(604800).timeOut(3).build());
        return token;
     }
}
