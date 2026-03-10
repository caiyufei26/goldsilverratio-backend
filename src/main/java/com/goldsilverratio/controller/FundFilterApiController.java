package com.goldsilverratio.controller;

import com.goldsilverratio.common.Result;
import com.goldsilverratio.service.FundFilterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 基金持仓筛选 API：C（每股收益同比）、A（年度每股收益）。
 */
@RestController
@RequestMapping("/api/fund-filter")
public class FundFilterApiController {

    private final FundFilterService fundFilterService;

    public FundFilterApiController(FundFilterService fundFilterService) {
        this.fundFilterService = fundFilterService;
    }

    /**
     * C - 查询基金持仓并按每股收益同比增长率筛选。
     *
     * @param code      基金代码，如 110011
     * @param threshold 同比增长率阈值（%），默认 25
     * @return 筛选结果
     */
    @GetMapping("/query")
    public Result<Map<String, Object>> query(
            @RequestParam("code") String code,
            @RequestParam(value = "threshold", defaultValue = "25") double threshold) {
        if (code == null || code.trim().isEmpty()) {
            return Result.fail(400, "基金代码不能为空");
        }
        code = code.trim();
        try {
            Map<String, Object> data = fundFilterService.queryAndFilter(code, threshold);
            if (data.containsKey("error")) {
                return Result.fail(400, (String) data.get("error"));
            }
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * A - 年度每股收益：近三年 > 25%（原版）或 去年>10% 且 今年>50%（放宽）。
     *
     * @param code 基金代码
     * @return 符合 A 的股票及原版/放宽标记
     */
    @GetMapping("/query-a")
    public Result<Map<String, Object>> queryA(@RequestParam("code") String code) {
        if (code == null || code.trim().isEmpty()) {
            return Result.fail(400, "基金代码不能为空");
        }
        code = code.trim();
        try {
            Map<String, Object> data = fundFilterService.queryAndFilterA(code);
            if (data.containsKey("error")) {
                return Result.fail(400, (String) data.get("error"));
            }
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * I - 机构认同度：基金持仓中正处于大股东减持计划执行期间的股票列表。
     *
     * @param code 基金代码
     * @return 处于减持计划执行期的持仓及计划摘要
     */
    @GetMapping("/query-i")
    public Result<Map<String, Object>> queryI(@RequestParam("code") String code) {
        if (code == null || code.trim().isEmpty()) {
            return Result.fail(400, "基金代码不能为空");
        }
        code = code.trim();
        try {
            Map<String, Object> data = fundFilterService.queryAndFilterI(code);
            if (data.containsKey("error")) {
                return Result.fail(400, (String) data.get("error"));
            }
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 查询单只股票是否处于大股东减持计划执行期间。
     *
     * @param stock 股票代码，如 300059
     * @return inReductionPlan 及计划摘要（若有）
     */
    @GetMapping("/check-reduction")
    public Result<Map<String, Object>> checkReduction(@RequestParam("stock") String stock) {
        if (stock == null || stock.trim().isEmpty()) {
            return Result.fail(400, "股票代码不能为空");
        }
        stock = stock.trim();
        try {
            Map<String, Object> data = fundFilterService.checkStockReduction(stock);
            if (data.containsKey("error")) {
                return Result.fail(400, (String) data.get("error"));
            }
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(500, "查询失败：" + e.getMessage());
        }
    }
}
