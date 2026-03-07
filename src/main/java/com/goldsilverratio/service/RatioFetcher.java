package com.goldsilverratio.service;

/**
 * 金银比数据拉取：从 GoldAPI 获取指定日期的金价、银价并写入库。
 */
public interface RatioFetcher {

    /**
     * 拉取指定日期的金银比并保存（周末自动按前一交易日）。日期格式 yyyyMMdd。
     *
     * @param dateStr 日期 yyyyMMdd，可为 null 表示当天
     * @return 成功描述或错误信息
     */
    String fetchAndSave(String dateStr);
}
