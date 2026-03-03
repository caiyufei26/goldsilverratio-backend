package com.goldsilverratio.mapper;

import com.goldsilverratio.entity.GoldSilverRatio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 金银比表 Mapper。
 */
@Mapper
public interface GoldSilverRatioMapper {

    /**
     * 插入一条记录。
     */
    int insert(GoldSilverRatio record);

    /**
     * 查询最新一条，按 record_date、record_time 倒序。
     */
    GoldSilverRatio selectLatest();

    /**
     * 分页查询，按 record_date、record_time 倒序。
     */
    List<GoldSilverRatio> selectPage(@Param("offset") int offset,
                                     @Param("size") int size);

    /**
     * 删除指定日期（yyyyMMdd）当天的记录，用于“同日期覆盖”。
     */
    int deleteByDate(@Param("dateStr") String dateStr);
}
