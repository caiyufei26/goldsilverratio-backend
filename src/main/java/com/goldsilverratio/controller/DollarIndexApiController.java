package com.goldsilverratio.controller;

import com.goldsilverratio.common.Result;
import com.goldsilverratio.service.DollarIndexApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 美元指数 API：手动录入、按月获取、列表。
 */
@RestController
@RequestMapping("/api/dollar-index")
public class DollarIndexApiController {

    private final DollarIndexApiService dollarIndexApiService;

    public DollarIndexApiController(DollarIndexApiService dollarIndexApiService) {
        this.dollarIndexApiService = dollarIndexApiService;
    }

    /**
     * 保存指定日期的收盘价。body: { "date": "yyyyMMdd", "closePrice": number }
     */
    @PostMapping("/feed")
    public Result<Void> feed(@RequestBody Map<String, Object> body) {
        Object d = body.get("date");
        Object c = body.get("closePrice");
        if (d == null || !(d instanceof String)) {
            return Result.fail(400, "缺少 date (yyyyMMdd)");
        }
        String dateStr = (String) d;
        if (dateStr.length() != 8) {
            return Result.fail(400, "date 格式须为 yyyyMMdd");
        }
        java.math.BigDecimal closePrice = toBigDecimal(c);
        if (closePrice == null) {
            return Result.fail(400, "closePrice 须为有效数字");
        }
        try {
            dollarIndexApiService.saveByDate(dateStr, closePrice);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 分页列表，按日期倒序。返回 data: [{ recordDate, closePrice }]
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        List<Map<String, Object>> data = dollarIndexApiService.listPage(page, size);
        return Result.ok(data);
    }

    /**
     * 按月从 FRED 获取数据（预留）。
     */
    @GetMapping("/fetch-month")
    public Result<String> fetchMonth(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        String message = dollarIndexApiService.fetchMonth(year, month);
        return Result.ok(message);
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
