package com.canLite.controller;

import com.canLite.common.Result;
import com.canLite.entity.PriceSimulation;
import com.canLite.service.PriceSimulationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 价格模拟 API。
 */
@RestController
@RequestMapping("/api/price-simulation")
public class PriceSimulationApiController {

    private final PriceSimulationService priceSimulationService;

    public PriceSimulationApiController(PriceSimulationService priceSimulationService) {
        this.priceSimulationService = priceSimulationService;
    }

    @GetMapping("/list")
    public Result<List<PriceSimulation>> list() {
        return Result.ok(priceSimulationService.listAll());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.fail(400, "id 无效");
        }
        boolean ok = priceSimulationService.deleteById(id);
        if (!ok) {
            return Result.fail(404, "记录不存在或已删除");
        }
        return Result.ok();
    }

    @PostMapping("/calculate")
    public Result<PriceSimulation> calculate(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return Result.fail(400, "请求体不能为空");
        }
        Object codeObj = body.get("stockCode");
        Object priceObj = body.get("purchasePrice");
        if (codeObj == null) {
            return Result.fail(400, "股票代码不能为空");
        }
        if (priceObj == null) {
            return Result.fail(400, "购买价格不能为空");
        }
        String stockCode = String.valueOf(codeObj).trim();
        BigDecimal purchasePrice;
        if (priceObj instanceof Number) {
            purchasePrice = BigDecimal.valueOf(((Number) priceObj).doubleValue());
        } else {
            try {
                purchasePrice = new BigDecimal(String.valueOf(priceObj).trim());
            } catch (NumberFormatException e) {
                return Result.fail(400, "购买价格格式无效");
            }
        }
        try {
            PriceSimulation data = priceSimulationService.calculateAndSave(stockCode, purchasePrice);
            return Result.ok(data);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            return Result.fail(500, "计算或保存失败：" + e.getMessage());
        }
    }
}
