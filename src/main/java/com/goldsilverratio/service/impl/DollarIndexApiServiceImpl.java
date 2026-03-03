package com.goldsilverratio.service.impl;

import com.goldsilverratio.entity.DollarIndex;
import com.goldsilverratio.mapper.DollarIndexMapper;
import com.goldsilverratio.service.DollarIndexApiService;
import com.goldsilverratio.service.DollarIndexFetcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final DateTimeFormatter RECORD_DATE_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DollarIndexMapper dollarIndexMapper;
    private final DollarIndexFetcher dollarIndexFetcher;

    public DollarIndexApiServiceImpl(DollarIndexMapper dollarIndexMapper,
                                    DollarIndexFetcher dollarIndexFetcher) {
        this.dollarIndexMapper = dollarIndexMapper;
        this.dollarIndexFetcher = dollarIndexFetcher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int fetchMonth(int year, int month) {
        List<Map<String, Object>> data = dollarIndexFetcher.fetchMonth(year, month);
        if (data == null || data.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> row : data) {
            Object d = row.get("date");
            Object c = row.get("closePrice");
            if (d == null || !(d instanceof String)) {
                continue;
            }
            String dateStr = (String) d;
            if (dateStr.length() != 8) {
                continue;
            }
            BigDecimal closePrice = toBigDecimal(c);
            if (closePrice == null) {
                continue;
            }
            saveByDate(dateStr, closePrice);
            count++;
        }
        return count;
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
        record.setRecordTime(LocalDateTime.now());
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
                    r.getRecordDate() == null ? "" : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("closePrice", r.getClosePrice());
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listByMonth(int year, int month) {
        List<DollarIndex> list = dollarIndexMapper.selectByMonth(year, month);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (DollarIndex r : list) {
            Map<String, Object> row = new HashMap<>(2);
            row.put("recordDate",
                    r.getRecordDate() == null ? "" : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("closePrice", r.getClosePrice());
            result.add(row);
        }
        return result;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return BigDecimal.valueOf(((Number) o).doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
