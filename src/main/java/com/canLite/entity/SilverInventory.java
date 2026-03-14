package com.canLite.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 上期所白银库存实体，对应表 silver_inventory。
 */
@Data
public class SilverInventory {

    private Long id;
    private LocalDate recordDate;
    private BigDecimal inventoryKg;
}
