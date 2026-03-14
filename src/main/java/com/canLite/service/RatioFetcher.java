package com.canLite.service;

/**
 * 金银比数据拉取：从上期所获取指定日期的金价、银价并写入库。
 */
public interface RatioFetcher {

    /**
     * 拉取指定日期的金银比并保存（周末自动按前一交易日）。日期格式 yyyyMMdd。
     *
     * @param dateStr 日期 yyyyMMdd，可为 null 表示当天
     * @return 成功描述或错误信息
     */
    String fetchAndSave(String dateStr);

    /**
     * 拉取指定月份内所有交易日的金银比并保存（逐日轮询，跳过周末）。
     *
     * @param year  年
     * @param month 月
     * @return 汇总信息，如 "已保存 22 个交易日，0 失败"
     */
    String fetchMonth(int year, int month);
}
