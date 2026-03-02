package com.goldsilverratio.service;

import com.goldsilverratio.entity.GoldSilverRatio;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 金银比服务单元测试。
 */
@SpringBootTest
class GoldSilverRatioServiceTest {

    @Resource
    private GoldSilverRatioService goldSilverRatioService;

    @Test
    void getLatest() {
        assertNotNull(goldSilverRatioService);
        GoldSilverRatio latest = goldSilverRatioService.getLatest();
        // 无数据时 latest 可能为 null
    }

    @Test
    void listPage() {
        List<GoldSilverRatio> list = goldSilverRatioService.listPage(1, 10);
        assertNotNull(list);
    }
}
