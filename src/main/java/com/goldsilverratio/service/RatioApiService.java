package com.goldsilverratio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 金银比 API 业务：按日期写入、分页列表。
 */
public interface RatioApiService {

    /**
     * 按日期保存一条金银比（金价、银价、日期 yyyyMMdd），同日期覆盖。
     *
     * @param gold  金价
     * @param silver 银价
     * @param dateStr 日期 yyyyMMdd
     */
    void saveByDate(BigDecimal gold, BigDecimal silver, String dateStr);

    /**
     * 分页列表，每项含 recordDate(yyyyMMdd)、goldPrice、silverPrice、ratio。
     */
    List<Map<String, Object>> listPage(int page, int size);
}
