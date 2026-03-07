package com.goldsilverratio.service;

import java.util.List;
import java.util.Map;

/**
 * 上期所燃油库存 API 业务。
 */
public interface FuelInventoryApiService {

    /**
     * 按年月列表，每项含 recordDate、inventoryKg、changeFromPrev。
     */
    List<Map<String, Object>> listByMonth(int year, int month);

    /**
     * 从上期所获取指定日期的燃油库存分仓库明细。
     *
     * @param dateStr 日期 yyyyMMdd
     * @return [{ region, warehouse, futuresKg, changeKg, rowType }]，无数据返回 null
     */
    List<Map<String, Object>> getDetailFromShfe(String dateStr);
}
