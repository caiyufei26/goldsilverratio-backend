package com.canLite.mapper;

import com.canLite.entity.PriceSimulation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 价格模拟记录 Mapper。
 */
public interface PriceSimulationMapper {

    int insert(PriceSimulation row);

    List<PriceSimulation> selectAll();

    int deleteById(@Param("id") Long id);
}
