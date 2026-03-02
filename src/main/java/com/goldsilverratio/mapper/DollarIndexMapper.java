package com.goldsilverratio.mapper;

import com.goldsilverratio.entity.DollarIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 美元指数表 Mapper。
 */
@Mapper
public interface DollarIndexMapper {

    /**
     * 插入一条记录。
     */
    int insert(DollarIndex record);

    /**
     * 删除指定日期的记录。
     */
    int deleteByDate(@Param("recordDate") java.time.LocalDate recordDate);

    /**
     * 按 record_date 倒序分页查询。
     */
    List<DollarIndex> selectPage(@Param("offset") int offset,
                                 @Param("size") int size);

    /**
     * 按年月查询，record_date 在该月内，按日期倒序。
     */
    List<DollarIndex> selectByMonth(@Param("year") int year,
                                    @Param("month") int month);
}
