package com.goldsilverratio.service;

import java.util.List;
import java.util.Map;

/**
 * 上期所每日交易数据 API 业务。
 */
public interface ShfeTradeDailyApiService {

    /**
     * 从上期所获取指定月份的每日交易数据并保存，按日循环获取。
     *
     * @param year  年
     * @param month 月
     * @return 提示信息
     */
    String fetchMonthFromShfe(int year, int month);

    /**
     * 按年月和品种查询交易数据列表。
     *
     * @param year      年
     * @param month     月
     * @param productId 品种代码（可选，为空时查所有品种）
     * @return [{ recordDate, productId, productName, volume, openInterest, oiChange }]
     */
    List<Map<String, Object>> listByMonth(int year, int month, String productId);

    /**
     * 查询数据库中已有的所有品种列表。
     *
     * @return [{ productId, productName }]
     */
    List<Map<String, Object>> listProducts();
}
