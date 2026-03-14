package com.canLite.mapper;

import com.canLite.entity.CftcCot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CFTC COT 表 Mapper。
 */
@Mapper
public interface CftcCotMapper {

    int insert(CftcCot record);

    int deleteByDateAndCommodity(@Param("recordDate") java.time.LocalDate recordDate,
                                 @Param("commodityCode") String commodityCode);

    List<CftcCot> selectPage(@Param("commodityCode") String commodityCode,
                             @Param("offset") int offset,
                             @Param("size") int size);

    List<CftcCot> selectByMonth(@Param("commodityCode") String commodityCode,
                                @Param("year") int year,
                                @Param("month") int month);

    CftcCot selectPrevByDate(@Param("recordDate") java.time.LocalDate recordDate,
                            @Param("commodityCode") String commodityCode);
}
