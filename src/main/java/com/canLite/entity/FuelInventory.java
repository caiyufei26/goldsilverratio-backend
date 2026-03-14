package com.canLite.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 上期所燃油库存实体，对应表 fuel_inventory。
 */
@Data
public class FuelInventory {

    private Long id;
    private LocalDate recordDate;
    private BigDecimal inventoryKg;
}
