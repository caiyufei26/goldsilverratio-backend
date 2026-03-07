package com.goldsilverratio.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 从上期所官网解析白银仓单日报，委托给 ShfeInventoryFetcher(ag)。
 */
@Component
public class ShfeSilverInventoryFetcher {

    private static final String PRODUCT_AG = "ag";

    private final ShfeInventoryFetcher shfeInventoryFetcher;

    public ShfeSilverInventoryFetcher(ShfeInventoryFetcher shfeInventoryFetcher) {
        this.shfeInventoryFetcher = shfeInventoryFetcher;
    }

    public BigDecimal fetchByDate(LocalDate date) {
        return shfeInventoryFetcher.fetchByDate(date, PRODUCT_AG);
    }

    public List<Map<String, Object>> fetchDetailByDate(LocalDate date) {
        return shfeInventoryFetcher.fetchDetailByDate(date, PRODUCT_AG);
    }
}
