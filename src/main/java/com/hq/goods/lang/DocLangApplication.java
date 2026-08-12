package com.hq.goods.lang;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动
 *
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class DocLangApplication {

    public static void main(String[] args){
        SpringApplication.run(DocLangApplication.class, args);
        log.info("startup success！");
    }

}

