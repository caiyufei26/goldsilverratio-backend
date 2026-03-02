package com.goldsilverratio.service.impl;

import com.goldsilverratio.entity.DollarIndex;
import com.goldsilverratio.mapper.DollarIndexMapper;
import com.goldsilverratio.service.DollarIndexApiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 美元指数 API 业务实现。
 */
@Service
public class DollarIndexApiServiceImpl implements DollarIndexApiService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DollarIndexMapper dollarIndexMapper;

    public DollarIndexApiServiceImpl(DollarIndexMapper dollarIndexMapper) {
        this.dollarIndexMapper = dollarIndexMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveByDate(String dateStr, BigDecimal closePrice) {
        if (closePrice == null) {
            throw new IllegalArgumentException("closePrice required");
        }
        LocalDate recordDate = LocalDate.parse(dateStr, YYYYMMDD);
        dollarIndexMapper.deleteByDate(recordDate);
        DollarIndex record = new DollarIndex();
        record.setRecordDate(recordDate);
        record.setClosePrice(closePrice);
        dollarIndexMapper.insert(record);
    }

    @Override
    public List<Map<String, Object>> listPage(int page, int size) {
        int offset = (page <= 0 ? 0 : page - 1) * (size <= 0 ? 100 : size);
        int limit = size <= 0 ? 100 : Math.min(size, 500);
        List<DollarIndex> list = dollarIndexMapper.selectPage(offset, limit);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (DollarIndex r : list) {
            Map<String, Object> row = new HashMap<>(2);
            row.put("recordDate",
                    r.getRecordDate() == null ? "" : r.getRecordDate().format(YYYYMMDD));
            row.put("closePrice", r.getClosePrice());
            result.add(row);
        }
        return result;
    }

    @Override
    public String fetchMonth(int year, int month) {
        // 预留：调用 FRED API 获取该月数据并批量 saveByDate；无数据返回提示
        return "该功能需配置 FRED API 后实现，当前请使用手动录入";
    }
}