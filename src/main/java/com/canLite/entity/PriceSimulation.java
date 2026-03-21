package com.canLite.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格模拟记录，对应表 price_simulation。
 */
@Data
public class PriceSimulation {

    private Long id;
    private String stockCode;
    private String stockName;
    private BigDecimal purchasePrice;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private BigDecimal histHigh;
    private BigDecimal dynamicTakeProfit;
    private BigDecimal lastClose;
    private BigDecimal dailyTakeProfit;
    private LocalDateTime createdAt;
}
