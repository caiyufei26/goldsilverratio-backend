package com.canLite.service.impl;

import com.canLite.service.RatioApiService;
import com.canLite.service.RatioFetcher;
import com.canLite.service.ShfeTradeDailyFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 从上期所拉取指定日期黄金(au)、白银(ag)主力合约结算价并保存为金银比。
 * 数据源：上期所每日行情 kx 数据，金价、银价单位统一为 元/克。
 */
@Service
public class RatioFetcherImpl implements RatioFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(RatioFetcherImpl.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RatioApiService ratioApiService;
    private final ShfeTradeDailyFetcher shfeTradeDailyFetcher;

    public RatioFetcherImpl(RatioApiService ratioApiService,
                            ShfeTradeDailyFetcher shfeTradeDailyFetcher) {
        this.ratioApiService = ratioApiService;
        this.shfeTradeDailyFetcher = shfeTradeDailyFetcher;
    }

    @Override
    public String fetchAndSave(String dateStr) {
        LocalDate date;
        if (dateStr == null || dateStr.trim().isEmpty()) {
            date = LocalDate.now();
        } else {
            try {
                date = LocalDate.parse(dateStr.trim(), YYYYMMDD);
            } catch (Exception e) {
                return "日期格式错误，应为 yyyyMMdd";
            }
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            LOG.debug("周末不请求接口，跳过: {}", date.format(YYYYMMDD));
            return "已跳过 " + date.format(YYYYMMDD) + " (周末)";
        }

        LocalDate tradeDate = toTradeDate(date);
        String tradeDateStr = tradeDate.format(YYYYMMDD);

        try {
            Map<String, BigDecimal> prices = shfeTradeDailyFetcher.fetchAuAgSettlementPrices(tradeDate);
            BigDecimal gold = prices.get("au");
            BigDecimal silver = prices.get("ag");
            if (gold == null || silver == null || silver.compareTo(BigDecimal.ZERO) <= 0) {
                return "上期所该日无有效黄金/白银结算价（可能非交易日或数据未发布）";
            }
            ratioApiService.saveByDate(gold, silver, tradeDateStr);
            LOG.info("金银比已拉取并保存: {} 金={} 银={} 元/克", tradeDateStr, gold, silver);
            return "已保存 " + tradeDateStr;
        } catch (Exception e) {
            LOG.warn("金银比拉取失败: {}", e.getMessage());
            return "拉取失败: " + e.getMessage();
        }
    }

    @Override
    public String fetchMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate today = LocalDate.now();
        LocalDate end = ym.atEndOfMonth();
        if (end.isAfter(today)) {
            end = today;
        }
        int ok = 0;
        int fail = 0;
        LocalDate day = ym.atDay(1);
        while (!day.isAfter(end)) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                day = day.plusDays(1);
                continue;
            }
            String dateStr = day.format(YYYYMMDD);
            String msg = fetchAndSave(dateStr);
            if (msg != null && msg.startsWith("已保存")) {
                ok++;
            } else {
                fail++;
            }
            day = day.plusDays(1);
        }
        return String.format("已保存 %d 个交易日，%d 失败", ok, fail);
    }

    private static LocalDate toTradeDate(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return d.minusDays(2);
        }
        if (dow == DayOfWeek.SATURDAY) {
            return d.minusDays(1);
        }
        return d;
    }
}
