package com.canLite.controller;

import com.canLite.common.Result;
import com.canLite.service.FundFilterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
     * S - 回购：基金持仓中发生过回购且今日在回购期间内的股票列表。
     *
     * @param code 基金代码
     * @return 今日在回购期间内的持仓及 repurchaseTypeMap
     */
    @GetMapping("/query-s")
    public Result<Map<String, Object>> queryS(@RequestParam("code") String code) {
        if (code == null || code.trim().isEmpty()) {
            return Result.fail(400, "基金代码不能为空");
        }
        code = code.trim();
        try {
            Map<String, Object> data = fundFilterService.queryAndFilterS(code);
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

    /**
     * 查询单只股票的 RPS60、RPS125、RPS250（个股相对涨幅强度）。
     *
     * @param stock 股票代码，如 002028
     * @return return60/125/250（区间涨跌幅%）、rps60/125/250（0~100 排名百分比，rps60 可能由全 A 排名计算）
     */
    @GetMapping("/rps")
    public Result<Map<String, Object>> getRps(@RequestParam("stock") String stock) {
        if (stock == null || stock.trim().isEmpty()) {
            return Result.fail(400, "股票代码不能为空");
        }
        stock = stock.trim();
        try {
            Map<String, Object> data = fundFilterService.getStockRps(stock);
            if (data.containsKey("error")) {
                return Result.fail(400, (String) data.get("error"));
            }
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 批量查询多只股票的 RPS60、RPS125、RPS250。用于基金持仓页展示持仓 RPS 列表。
     *
     * @param body 请求体，如 {"stocks": ["002028", "600519"]}
     * @return 列表，每项含 stockCode、stockName、rps60、rps125、rps250
     */
    @PostMapping("/rps-batch")
    @SuppressWarnings("unchecked")
    public Result<List<Map<String, Object>>> getRpsBatch(@RequestBody Map<String, Object> body) {
        Object stocksObj = body != null ? body.get("stocks") : null;
        if (!(stocksObj instanceof List)) {
            return Result.fail(400, "请提供 stocks 数组");
        }
        List<String> stocks = new java.util.ArrayList<>();
        for (Object o : (List<?>) stocksObj) {
            if (o != null) {
                stocks.add(o.toString().trim());
            }
        }
        try {
            List<Map<String, Object>> data = fundFilterService.getStockRpsBatch(stocks);
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(500, "批量查询失败：" + e.getMessage());
        }
    }
}
