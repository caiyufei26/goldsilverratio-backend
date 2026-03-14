package com.canLite.mapper;

import com.canLite.entity.ShfeTradeDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 上期所每日交易数据 Mapper。
 */
@Mapper
public interface ShfeTradeDailyMapper {

    /**
     * 插入一条记录。
     */
    int insert(ShfeTradeDaily record);

    /**
     * 删除指定日期和品种的记录。
     */
    int deleteByDateAndProduct(@Param("recordDate") LocalDate recordDate,
                               @Param("productId") String productId);

    /**
     * 删除指定日期的所有品种记录。
     */
    int deleteByDate(@Param("recordDate") LocalDate recordDate);

    /**
     * 按年月和品种查询，record_date 在该月内，按日期升序。
     */
    List<ShfeTradeDaily> selectByMonthAndProduct(@Param("year") int year,
                                                  @Param("month") int month,
                                                  @Param("productId") String productId);

    /**
     * 按年月查询所有品种，按日期升序、品种排序。
     */
    List<ShfeTradeDaily> selectByMonth(@Param("year") int year,
                                       @Param("month") int month);

    /**
     * 查询指定日期的所有品种数据。
     */
    List<ShfeTradeDaily> selectByDate(@Param("recordDate") LocalDate recordDate);

    /**
     * 查询指定日期所有不重复的品种ID列表。
     */
    List<String> selectDistinctProducts();
}
