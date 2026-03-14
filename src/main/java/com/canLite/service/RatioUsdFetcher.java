package com.canLite.service;

/**
 * 美元计价金银比数据拉取：从 GoldAPI 获取 XAU/USD、XAG/USD 并写入库。
 */
public interface RatioUsdFetcher {

    /**
     * 拉取指定日期的金银比（GoldAPI 历史价）并保存。日期格式 yyyyMMdd。
     *
     * @param dateStr 日期 yyyyMMdd，可为 null 表示当天
     * @return 成功描述或错误信息
     */
    String fetchAndSave(String dateStr);

    /**
     * 拉取指定月份内每日的金银比并保存（逐日请求 GoldAPI）。
     *
     * @param year  年
     * @param month 月
     * @return 汇总信息
     */
    String fetchMonth(int year, int month);
}
