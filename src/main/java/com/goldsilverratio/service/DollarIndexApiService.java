package com.goldsilverratio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 美元指数 API 业务：录入、按月拉取、列表。
 */
public interface DollarIndexApiService {

    /**
     * 保存指定日期的收盘价，同日期覆盖。
     *
     * @param dateStr   日期 yyyyMMdd
     * @param closePrice 收盘价
     */
    void saveByDate(String dateStr, BigDecimal closePrice);

    /**
     * 分页列表，每项含 recordDate(yyyyMMdd)、closePrice。
     */
    List<Map<String, Object>> listPage(int page, int size);

    /**
     * 从 FRED 获取指定年月数据并入库，无数据则不保存。
     *
     * @param year  年
     * @param month 月
     * @return 提示信息
     */
    String fetchMonth(int year, int month);
}
