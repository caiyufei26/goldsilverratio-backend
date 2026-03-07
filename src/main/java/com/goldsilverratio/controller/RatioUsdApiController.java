package com.goldsilverratio.controller;

import com.goldsilverratio.common.Result;
import com.goldsilverratio.service.RatioUsdApiService;
import com.goldsilverratio.service.RatioUsdFetcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 美元计价金银比 API：按日期拉取（GoldAPI）、按日期写入、分页/按月列表。
 * 不参与一键同步，仅本模块单独同步。
 */
@RestController
@RequestMapping("/api/ratio-usd")
public class RatioUsdApiController {

    private final RatioUsdApiService ratioUsdApiService;
    private final RatioUsdFetcher ratioUsdFetcher;

    public RatioUsdApiController(RatioUsdApiService ratioUsdApiService,
                                 RatioUsdFetcher ratioUsdFetcher) {
        this.ratioUsdApiService = ratioUsdApiService;
        this.ratioUsdFetcher = ratioUsdFetcher;
    }

    /**
     * 按日期从 GoldAPI 拉取并保存。不传 date 则拉取当日。
     */
    @PostMapping("/fetch-date")
    public Result<String> fetchDate(@RequestParam(value = "date", required = false) String date) {
        String msg = ratioUsdFetcher.fetchAndSave(date);
        if (msg != null && (msg.startsWith("已保存") || msg.startsWith("已跳过"))) {
            return Result.ok(msg);
        }
        return Result.fail(400, msg != null ? msg : "拉取失败");
    }

    /**
     * 按月份逐日从 GoldAPI 拉取并保存。
     */
    @PostMapping("/fetch-month")
    public Result<String> fetchMonth(@RequestParam("year") int year,
                                    @RequestParam("month") int month) {
        String msg = ratioUsdFetcher.fetchMonth(year, month);
        return Result.ok(msg);
    }

    /**
     * 手动录入一条。body: { "gold": number, "silver": number, "date": "yyyyMMdd" }
     */
    @PostMapping("/feed")
    public Result<Void> feed(@RequestBody Map<String, Object> body) {
        Object g = body.get("gold");
        Object s = body.get("silver");
        Object d = body.get("date");
        if (g == null || s == null || d == null || !(d instanceof String)) {
            return Result.fail(400, "缺少 gold / silver / date");
        }
        String dateStr = (String) d;
        if (dateStr.length() != 8) {
            return Result.fail(400, "date 格式须为 yyyyMMdd");
        }
        java.math.BigDecimal gold = toBigDecimal(g);
        java.math.BigDecimal silver = toBigDecimal(s);
        if (gold == null || silver == null || silver.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return Result.fail(400, "gold/silver 须为有效正数");
        }
        try {
            ratioUsdApiService.saveByDate(gold, silver, dateStr, "manual");
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 列表。传 year、month 时按该月筛选；否则分页。
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        List<Map<String, Object>> data;
        if (year != null && month != null) {
            data = ratioUsdApiService.listByMonth(year, month);
        } else {
            data = ratioUsdApiService.listPage(page, size);
        }
        return Result.ok(data);
    }

    private static java.math.BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) o).doubleValue());
        }
        try {
            return new java.math.BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
