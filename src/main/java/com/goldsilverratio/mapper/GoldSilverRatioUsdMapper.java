package com.goldsilverratio.mapper;

import com.goldsilverratio.entity.GoldSilverRatioUsd;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 美元计价金银比表 Mapper。
 */
@Mapper
public interface GoldSilverRatioUsdMapper {

    int insert(GoldSilverRatioUsd record);

    List<GoldSilverRatioUsd> selectPage(@Param("offset") int offset,
                                        @Param("size") int size);

    List<GoldSilverRatioUsd> selectByMonth(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    int deleteByDate(@Param("dateStr") String dateStr);
}
