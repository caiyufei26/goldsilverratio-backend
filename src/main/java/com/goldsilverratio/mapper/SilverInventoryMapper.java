package com.goldsilverratio.mapper;

import com.goldsilverratio.entity.SilverInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 白银库存表 Mapper。
 */
@Mapper
public interface SilverInventoryMapper {

    /**
     * 插入一条记录。
     */
    int insert(SilverInventory record);

    /**
     * 删除指定日期的记录。
     */
    int deleteByDate(@Param("recordDate") java.time.LocalDate recordDate);

    /**
     * 按 record_date 倒序分页查询。
     */
    List<SilverInventory> selectPage(@Param("offset") int offset,
                                     @Param("size") int size);

    /**
     * 按年月查询，record_date 在该月内，按日期倒序。
     */
    List<SilverInventory> selectByMonth(@Param("year") int year,
                                       @Param("month") int month);

    /**
     * 查询指定日期的前一条记录（前一交易日库存）。
     */
    SilverInventory selectPrevByDate(@Param("recordDate") java.time.LocalDate recordDate);
}
