package com.canLite.service.impl;

import com.canLite.entity.FuelInventory;
import com.canLite.mapper.FuelInventoryMapper;
import com.canLite.service.FuelInventoryApiService;
import com.canLite.service.ShfeInventoryFetcher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上期所燃油库存 API 业务实现。
 */
@Service
public class FuelInventoryApiServiceImpl implements FuelInventoryApiService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter RECORD_DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String PRODUCT_FU = "fu";

    private final FuelInventoryMapper fuelInventoryMapper;
    private final ShfeInventoryFetcher shfeInventoryFetcher;

    public FuelInventoryApiServiceImpl(FuelInventoryMapper fuelInventoryMapper,
                                      ShfeInventoryFetcher shfeInventoryFetcher) {
        this.fuelInventoryMapper = fuelInventoryMapper;
        this.shfeInventoryFetcher = shfeInventoryFetcher;
    }

    @Override
    public List<Map<String, Object>> listByMonth(int year, int month) {
        List<FuelInventory> list = fuelInventoryMapper.selectByMonth(year, month);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (FuelInventory r : list) {
            Map<String, Object> row = new HashMap<>(3);
            row.put("recordDate",
                    r.getRecordDate() == null ? "" : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("inventoryKg", r.getInventoryKg());
            row.put("changeFromPrev", computeChangeFromPrev(r.getRecordDate(), r.getInventoryKg()));
            result.add(row);
        }
        return result;
    }

    private BigDecimal computeChangeFromPrev(LocalDate recordDate, BigDecimal current) {
        if (recordDate == null || current == null) {
            return null;
        }
        FuelInventory prev = fuelInventoryMapper.selectPrevByDate(recordDate);
        if (prev == null || prev.getInventoryKg() == null) {
            return null;
        }
        return current.subtract(prev.getInventoryKg());
    }

    @Override
    public List<Map<String, Object>> getDetailFromShfe(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return null;
        }
        LocalDate date = LocalDate.parse(dateStr, YYYYMMDD);
        return shfeInventoryFetcher.fetchDetailByDate(date, PRODUCT_FU);
    }
}
