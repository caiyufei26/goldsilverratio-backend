package com.goldsilverratio.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CFTC COT 持仓报告实体，对应表 cftc_cot。
 */
@Data
public class CftcCot {

    private Long id;
    private LocalDate recordDate;
    private String commodityCode;
    private Long totalOpenInterest;
    private Long fundLong;
    private Long fundShort;
    private Long fundNet;
    private Long commercialLong;
    private Long commercialShort;
    private Integer reportableTraders;
    private LocalDateTime recordTime;
}
