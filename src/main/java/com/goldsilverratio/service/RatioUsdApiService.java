package com.goldsilverratio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 美元计价金银比 API 业务：按日期写入、分页/按月列表。
 */
public interface RatioUsdApiService {

    /**
     * 按日期覆盖保存一条（金价、银价单位：美元/盎司）。
     */
    void saveByDate(BigDecimal gold, BigDecimal silver, String dateStr);

    /**
     * 分页列表，每项含 recordDate、goldPrice、silverPrice、ratio。
     */
    List<Map<String, Object>> listPage(int page, int size);

    /**
     * 按年月列表，按日期升序。
     */
    List<Map<String, Object>> listByMonth(int year, int month);
}
