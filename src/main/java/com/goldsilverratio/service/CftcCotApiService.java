package com.goldsilverratio.service;

import java.util.List;
import java.util.Map;

/**
 * CFTC COT 持仓报告 API 业务。
 */
public interface CftcCotApiService {

    /**
     * 保存一条 COT 记录，同日期同品种覆盖。
     */
    void save(Map<String, Object> body);

    /**
     * 列表，含较上周变动。可按 commodity、year、month 筛选。
     */
    List<Map<String, Object>> list(String commodityCode, Integer year, Integer month, int page, int size);

    /**
     * 从 CFTC 官网自动获取最新 COT 数据并保存。
     *
     * @return 保存成功的条数
     */
    int fetchFromCftc();
}
