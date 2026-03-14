package com.canLite.entity;

import lombok.Data;

import java.time.LocalDate;

/**
 * 上期所每日交易数据实体，对应表 shfe_trade_daily。
 */
@Data
public class ShfeTradeDaily {

    private Long id;
    private LocalDate recordDate;
    private String productId;
    private String productName;
    private Long volume;
    private Long openInterest;
    private Long oiChange;
}
