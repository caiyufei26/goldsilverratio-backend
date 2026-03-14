package com.canLite.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 上期所白银库存 API 业务。
 */
public interface SilverInventoryApiService {

    /**
     * 保存指定日期的库存（千克），同日期覆盖。
     *
     * @param dateStr    日期 yyyyMMdd
     * @param inventoryKg 库存千克
     */
    void saveByDate(String dateStr, BigDecimal inventoryKg);

    /**
     * 分页列表，每项含 recordDate、inventoryKg、changeFromPrev（较前日变动）。
     */
    List<Map<String, Object>> listPage(int page, int size);

    /**
     * 按年月列表，每项含 recordDate、inventoryKg、changeFromPrev。
     */
    List<Map<String, Object>> listByMonth(int year, int month);

    /**
     * 从上期所获取指定日期的白银库存并保存。无数据则不保存。
     *
     * @param dateStr 日期 yyyyMMdd
     * @return 提示信息
     */
    String fetchFromShfe(String dateStr);

    /**
     * 从上期所获取指定月份的白银库存并保存，按日循环获取。
     *
     * @param year  年
     * @param month 月
     * @return 提示信息
     */
    String fetchMonthFromShfe(int year, int month);

    /**
     * 从上期所获取指定日期的白银库存分仓库明细。
     *
     * @param dateStr 日期 yyyyMMdd
     * @return [{ region, warehouse, futuresKg, changeKg, rowType }]，无数据返回 null
     */
    List<Map<String, Object>> getDetailFromShfe(String dateStr);
}
