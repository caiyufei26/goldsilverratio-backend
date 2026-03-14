package com.canLite.schedule;

import com.canLite.service.DollarIndexApiService;
import com.canLite.service.RatioFetcher;
import com.canLite.service.RatioUsdFetcher;
import com.canLite.service.SilverInventoryApiService;
import com.canLite.service.ShfeTradeDailyApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 数据同步定时任务：每日 17:30（上海时区）自动拉取，周末不执行。与「数据同步-同步该月数据」一致：
 * 1）当日金银比（上期所 au/ag 结算价）；
 * 2）当日美元计价金银比（GoldAPI/极速数据降级）；
 * 3）上期所本月交易数据（白银、燃油等）；
 * 4）上期所本月仓单库存（白银+燃油）；
 * 5）本月美元指数。
 */
@Component
public class ShfeTradeDailySchedule {

    private static final Logger LOG = LoggerFactory.getLogger(ShfeTradeDailySchedule.class);

    private final ShfeTradeDailyApiService shfeTradeDailyApiService;
    private final SilverInventoryApiService silverInventoryApiService;
    private final DollarIndexApiService dollarIndexApiService;
    private final RatioFetcher ratioFetcher;
    private final RatioUsdFetcher ratioUsdFetcher;

    public ShfeTradeDailySchedule(ShfeTradeDailyApiService shfeTradeDailyApiService,
                                  SilverInventoryApiService silverInventoryApiService,
                                  DollarIndexApiService dollarIndexApiService,
                                  RatioFetcher ratioFetcher,
                                  RatioUsdFetcher ratioUsdFetcher) {
        this.shfeTradeDailyApiService = shfeTradeDailyApiService;
        this.silverInventoryApiService = silverInventoryApiService;
        this.dollarIndexApiService = dollarIndexApiService;
        this.ratioFetcher = ratioFetcher;
        this.ratioUsdFetcher = ratioUsdFetcher;
    }

    @Scheduled(cron = "0 30 17 * * ?", zone = "Asia/Shanghai")
    public void fetchCurrentMonth() {
        LocalDate now = LocalDate.now();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            LOG.info("定时任务：今日周末，跳过同步");
            return;
        }

        int year = now.getYear();
        int month = now.getMonthValue();

        LOG.info("定时任务：开始自动拉取当日金银比");
        try {
            String ratioMsg = ratioFetcher.fetchAndSave(null);
            LOG.info("定时任务：金银比拉取完成 {}", ratioMsg);
        } catch (Exception e) {
            LOG.error("定时任务：金银比拉取失败", e);
        }

        LOG.info("定时任务：开始自动拉取当日美元计价金银比");
        try {
            String ratioUsdMsg = ratioUsdFetcher.fetchAndSave(null);
            LOG.info("定时任务：美元计价金银比拉取完成 {}", ratioUsdMsg);
        } catch (Exception e) {
            LOG.error("定时任务：美元计价金银比拉取失败", e);
        }

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
