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
     * 列表。若传 year、month 则按该月筛选；否则分页全量。
     * 返回 data: [{ recordDate, closePrice }]
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        List<Map<String, Object>> data;
        if (year != null && month != null) {
            data = dollarIndexApiService.listByMonth(year, month);
        } else {
            data = dollarIndexApiService.listPage(page, size);
        }
        return Result.ok(data);
    }

    /**
     * 保存前端从 Yahoo 获取的批量数据。body: { data: [{ date, closePrice }] }。
     */
    @PostMapping("/save-from-yahoo")
    public Result<String> saveFromYahoo(@RequestBody Map<String, Object> body) {
        Object dataObj = body.get("data");
        if (!(dataObj instanceof List)) {
            return Result.fail(400, "缺少 data 数组");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) dataObj;
        int count = dollarIndexApiService.saveBatchFromYahoo(data);
        return Result.ok("已保存 " + count + " 条");
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
