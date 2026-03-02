package com.goldsilverratio.controller;

import com.goldsilverratio.common.Result;
import com.goldsilverratio.service.RatioApiService;
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

    public RatioApiController(RatioApiService ratioApiService) {
        this.ratioApiService = ratioApiService;
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
     * 分页列表，按日期倒序。返回 data: [{ recordDate, goldPrice, silverPrice, ratio }]
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        List<Map<String, Object>> data = ratioApiService.listPage(page, size);
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
