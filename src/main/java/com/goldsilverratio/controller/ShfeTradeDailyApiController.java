package com.goldsilverratio.controller;

import com.goldsilverratio.common.Result;
import com.goldsilverratio.service.ShfeTradeDailyApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 上期所每日交易数据 API。
 */
@RestController
@RequestMapping("/api/shfe-trade")
public class ShfeTradeDailyApiController {

    private final ShfeTradeDailyApiService shfeTradeDailyApiService;

    public ShfeTradeDailyApiController(ShfeTradeDailyApiService shfeTradeDailyApiService) {
        this.shfeTradeDailyApiService = shfeTradeDailyApiService;
    }

    /**
     * 从上期所获取指定月份的每日交易数据并保存。
     */
    @GetMapping("/fetch-month")
    public Result<String> fetchMonth(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            String message = shfeTradeDailyApiService.fetchMonthFromShfe(year, month);
            return Result.ok(message);
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 按年月查询交易数据列表，可按品种筛选。
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam(value = "productId", required = false) String productId) {
        List<Map<String, Object>> data = shfeTradeDailyApiService.listByMonth(year, month, productId);
        return Result.ok(data);
    }

    /**
     * 查询已有的品种列表（用于前端下拉选择）。
     */
    @GetMapping("/products")
    public Result<List<Map<String, Object>>> products() {
        return Result.ok(shfeTradeDailyApiService.listProducts());
    }
}
