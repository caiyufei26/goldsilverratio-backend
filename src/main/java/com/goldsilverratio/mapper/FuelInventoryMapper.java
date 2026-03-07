package com.goldsilverratio.mapper;

import com.goldsilverratio.entity.FuelInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 燃油库存表 Mapper。
 */
@Mapper
public interface FuelInventoryMapper {

    int insert(FuelInventory record);

    int deleteByDate(@Param("recordDate") java.time.LocalDate recordDate);

    List<FuelInventory> selectPage(@Param("offset") int offset,
                                   @Param("size") int size);

    List<FuelInventory> selectByMonth(@Param("year") int year,
                                      @Param("month") int month);

    FuelInventory selectPrevByDate(@Param("recordDate") java.time.LocalDate recordDate);
}
