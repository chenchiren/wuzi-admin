package com.relex.wuzi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.relex.wuzi.mapper")
public class WuziApplication {
    public static void main(String[] args) {
        SpringApplication.run(WuziApplication.class, args);
    }
}
