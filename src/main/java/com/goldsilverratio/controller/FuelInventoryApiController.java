package com.goldsilverratio.controller;

import com.goldsilverratio.common.Result;
import com.goldsilverratio.service.FuelInventoryApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 上期所燃油库存 API。
 */
@RestController
@RequestMapping("/api/fuel-inventory")
public class FuelInventoryApiController {

    private final FuelInventoryApiService fuelInventoryApiService;

    public FuelInventoryApiController(FuelInventoryApiService fuelInventoryApiService) {
        this.fuelInventoryApiService = fuelInventoryApiService;
    }

    /**
     * 按年月列表。返回 data: [{ recordDate, inventoryKg, changeFromPrev }]
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        List<Map<String, Object>> data = fuelInventoryApiService.listByMonth(year, month);
        return Result.ok(data);
    }

    /**
     * 获取指定日期的燃油库存分仓库明细。从上期所实时拉取。
     */
    @GetMapping("/detail")
    public Result<List<Map<String, Object>>> detail(@RequestParam("date") String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return Result.fail(400, "date 格式须为 yyyyMMdd");
        }
        List<Map<String, Object>> data = fuelInventoryApiService.getDetailFromShfe(dateStr);
        if (data == null) {
            return Result.fail(404, "该日无数据或解析失败");
        }
        return Result.ok(data);
    }
}
