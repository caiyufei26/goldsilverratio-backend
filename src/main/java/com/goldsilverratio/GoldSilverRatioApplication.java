package com.goldsilverratio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 金银比后端启动类。
 */
@SpringBootApplication
@MapperScan("com.goldsilverratio.mapper")
public class GoldSilverRatioApplication {

    public static void main(String[] args) {
        // Java 8 访问 api.metals.live 需在任意 SSL 初始化前禁用 SNI，否则会报 unrecognized_name
        System.setProperty("jsse.enableSNIExtension", "false");
        // 强制 TLS 1.2，缓解访问 CFTC 等站点时的 handshake_failure
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        SpringApplication.run(GoldSilverRatioApplication.class, args);
    }
}
