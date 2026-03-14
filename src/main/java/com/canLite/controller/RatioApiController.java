package com.canLite.controller;

import com.canLite.common.Result;
import com.canLite.service.RatioApiService;
import com.canLite.service.RatioFetcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 金银比 API：按日期写入、分页列表。
 */
@RestController
@RequestMapping("/api/ratio")
public class RatioApiController {

    private final RatioApiService ratioApiService;
    private final RatioFetcher ratioFetcher;

    public RatioApiController(RatioApiService ratioApiService, RatioFetcher ratioFetcher) {
        this.ratioApiService = ratioApiService;
        this.ratioFetcher = ratioFetcher;
    }

    /**
     * 按日期从上期所拉取金银比并保存。不传 date 则拉取当日（按交易日折算）。
     *
     * @param date 可选，yyyyMMdd
     * @return 成功为 data 描述，失败为 message
     */
    @PostMapping("/fetch-date")
    public Result<String> fetchDate(@RequestParam(value = "date", required = false) String date) {
        String msg = ratioFetcher.fetchAndSave(date);
        if (msg != null && msg.startsWith("已保存")) {
            return Result.ok(msg);
        }
        return Result.fail(400, msg != null ? msg : "拉取失败");
    }

    /**
     * 按月份拉取金银比：遍历该月每个交易日逐日从上期所拉取并保存。
     *
     * @param year  年
     * @param month 月
     * @return 成功为 data 汇总信息，如 "已保存 22 个交易日，0 失败"
     */
    @PostMapping("/fetch-month")
    public Result<String> fetchMonth(@RequestParam("year") int year,
                                    @RequestParam("month") int month) {
        String msg = ratioFetcher.fetchMonth(year, month);
        return Result.ok(msg);
    }

    /**
     * 按日期保存一条金银比。body: { "gold": number, "silver": number, "date": "yyyyMMdd" }
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
            ratioApiService.saveByDate(gold, silver, dateStr);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 列表。若传 year、month 则按该月筛选（按日期升序）；否则分页全量倒序。
     * 返回 data: [{ recordDate, goldPrice, silverPrice, ratio }]
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        List<Map<String, Object>> data;
        if (year != null && month != null) {
            data = ratioApiService.listByMonth(year, month);
        } else {
            data = ratioApiService.listPage(page, size);
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
