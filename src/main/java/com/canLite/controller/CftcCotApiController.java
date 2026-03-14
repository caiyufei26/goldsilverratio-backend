package com.canLite.controller;

import com.canLite.common.Result;
import com.canLite.service.CftcCotApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * CFTC COT 持仓报告 API。
 */
@RestController
@RequestMapping("/api/cftc-cot")
public class CftcCotApiController {

    private final CftcCotApiService cftcCotApiService;

    public CftcCotApiController(CftcCotApiService cftcCotApiService) {
        this.cftcCotApiService = cftcCotApiService;
    }

    /**
     * 从 CFTC 官网自动获取最新 COT 数据并保存。
     */
    @GetMapping("/fetch")
    public Result<Map<String, Object>> fetch() {
        try {
            int saved = cftcCotApiService.fetchFromCftc();
            return Result.ok(java.util.Collections.singletonMap("saved", saved));
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 保存一条 COT 记录。
     */
    @PostMapping("/feed")
    public Result<Void> feed(@RequestBody Map<String, Object> body) {
        try {
            cftcCotApiService.save(body);
            return Result.ok();
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 列表。支持 commodity、year、month 筛选。
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "commodity", required = false) String commodity,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        List<Map<String, Object>> data = cftcCotApiService.list(commodity, year, month, page, size);
        return Result.ok(data);
    }
}
