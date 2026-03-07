package com.goldsilverratio.schedule;

import com.goldsilverratio.service.DollarIndexApiService;
import com.goldsilverratio.service.SilverInventoryApiService;
import com.goldsilverratio.service.ShfeTradeDailyApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 数据同步定时任务：每日 17:30（上海时区）自动拉取本月数据，与「数据同步-同步该月数据」一致（不含金银比）：
 * 1）上期所本月交易数据（白银、燃油等）；
 * 2）上期所本月仓单库存（白银+燃油）；
 * 3）本月美元指数。
 */
@Component
public class ShfeTradeDailySchedule {

    private static final Logger LOG = LoggerFactory.getLogger(ShfeTradeDailySchedule.class);

    private final ShfeTradeDailyApiService shfeTradeDailyApiService;
    private final SilverInventoryApiService silverInventoryApiService;
    private final DollarIndexApiService dollarIndexApiService;

    public ShfeTradeDailySchedule(ShfeTradeDailyApiService shfeTradeDailyApiService,
                                  SilverInventoryApiService silverInventoryApiService,
                                  DollarIndexApiService dollarIndexApiService) {
        this.shfeTradeDailyApiService = shfeTradeDailyApiService;
        this.silverInventoryApiService = silverInventoryApiService;
        this.dollarIndexApiService = dollarIndexApiService;
    }

    @Scheduled(cron = "0 30 17 * * ?", zone = "Asia/Shanghai")
    public void fetchCurrentMonth() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        LOG.info("定时任务：开始自动更新上期所本月交易数据 {}年{}月", year, month);
        try {
            String message = shfeTradeDailyApiService.fetchMonthFromShfe(year, month);
            LOG.info("定时任务：上期所本月交易数据更新完成 {}", message);
        } catch (Exception e) {
            LOG.error("定时任务：上期所本月交易数据更新失败", e);
        }

        LOG.info("定时任务：开始自动更新上期所本月库存（白银+燃油） {}年{}月", year, month);
        try {
            String invMessage = silverInventoryApiService.fetchMonthFromShfe(year, month);
            LOG.info("定时任务：上期所本月库存更新完成 {}", invMessage);
        } catch (Exception e) {
            LOG.error("定时任务：上期所本月库存更新失败", e);
        }

        LOG.info("定时任务：开始自动更新本月美元指数 {}年{}月", year, month);
        try {
            int dxyCount = dollarIndexApiService.fetchMonth(year, month);
            LOG.info("定时任务：本月美元指数更新完成，保存 {} 条", dxyCount);
        } catch (Exception e) {
            LOG.error("定时任务：本月美元指数更新失败", e);
        }
    }
}
