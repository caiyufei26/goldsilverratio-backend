package com.goldsilverratio.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 美元指数每日收盘价实体，对应表 dollar_index。
 */
@Data
public class DollarIndex {

    private Long id;
    private LocalDate recordDate;
    private BigDecimal closePrice;
}
