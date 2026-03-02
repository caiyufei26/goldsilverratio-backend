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
     * 按年月列表，每项含 recordDate(yyyyMMdd)、closePrice。
     */
    List<Map<String, Object>> listByMonth(int year, int month);

    /**
     * 批量保存前端从 Yahoo 获取的数据。body.data: [{ date, closePrice }]。
     *
     * @param data 数据列表
     * @return 保存条数
     */
    int saveBatchFromYahoo(List<Map<String, Object>> data);
}
