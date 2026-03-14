package com.canLite.controller;

import com.canLite.common.Result;
import com.canLite.service.SilverInventoryApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 上期所白银库存 API。
 */
@RestController
@RequestMapping("/api/silver-inventory")
public class SilverInventoryApiController {

    private final SilverInventoryApiService silverInventoryApiService;

    public SilverInventoryApiController(SilverInventoryApiService silverInventoryApiService) {
        this.silverInventoryApiService = silverInventoryApiService;
    }

    /**
     * 保存指定日期的库存。body: { "date": "yyyyMMdd", "inventoryKg": number }
     */
    @PostMapping("/feed")
    public Result<Void> feed(@RequestBody Map<String, Object> body) {
        Object d = body.get("date");
        Object v = body.get("inventoryKg");
        if (d == null || !(d instanceof String)) {
            return Result.fail(400, "缺少 date (yyyyMMdd)");
        }
        String dateStr = (String) d;
        if (dateStr.length() != 8) {
            return Result.fail(400, "date 格式须为 yyyyMMdd");
        }
        java.math.BigDecimal inventoryKg = toBigDecimal(v);
        if (inventoryKg == null) {
            return Result.fail(400, "inventoryKg 须为有效数字");
        }
        try {
            silverInventoryApiService.saveByDate(dateStr, inventoryKg);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 从上期所获取指定日期的白银库存并保存。
     */
    @GetMapping("/fetch")
    public Result<String> fetch(@RequestParam("date") String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return Result.fail(400, "date 格式须为 yyyyMMdd");
        }
        try {
            String message = silverInventoryApiService.fetchFromShfe(dateStr);
            return Result.ok(message);
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 从上期所获取指定月份的白银库存并保存。
     */
    @GetMapping("/fetch-month")
    public Result<String> fetchMonth(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            String message = silverInventoryApiService.fetchMonthFromShfe(year, month);
            return Result.ok(message);
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 获取指定日期的白银库存分仓库明细。从上期所实时拉取。
     * 返回 data: [{ region, warehouse, futuresKg, changeKg, rowType }]
     */
    @GetMapping("/detail")
    public Result<List<Map<String, Object>>> detail(@RequestParam("date") String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return Result.fail(400, "date 格式须为 yyyyMMdd");
        }
        List<Map<String, Object>> data = silverInventoryApiService.getDetailFromShfe(dateStr);
        if (data == null) {
            return Result.fail(404, "该日无数据或解析失败");
        }
        return Result.ok(data);
    }

    /**
     * 列表。若传 year、month 则按该月筛选；否则分页全量。
     * 返回 data: [{ recordDate, inventoryKg, changeFromPrev }]
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        List<Map<String, Object>> data;
        if (year != null && month != null) {
            data = silverInventoryApiService.listByMonth(year, month);
        } else {
            data = silverInventoryApiService.listPage(page, size);
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
