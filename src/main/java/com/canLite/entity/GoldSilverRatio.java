package com.canLite.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 金银比记录实体，对应表 gold_silver_ratio。
 */
@Data
public class GoldSilverRatio {

    private Long id;
    private BigDecimal goldPrice;
    private BigDecimal silverPrice;
    private BigDecimal ratio;
    /** 数据日期（业务日期），列表展示用 */
    private LocalDate recordDate;
    private LocalDateTime recordTime;
}
