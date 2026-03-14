package com.canLite.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 美元计价金银比记录实体，对应表 gold_silver_ratio_usd。
 * 金价、银价单位为美元/盎司（USD/oz）。dataSource 区分来源：goldapi / jisuapi / manual。
 */
@Data
public class GoldSilverRatioUsd {

    private Long id;
    private BigDecimal goldPrice;
    private BigDecimal silverPrice;
    private BigDecimal ratio;
    private LocalDate recordDate;
    private LocalDateTime recordTime;
    /** 数据来源：goldapi、jisuapi、manual */
    private String dataSource;
}
