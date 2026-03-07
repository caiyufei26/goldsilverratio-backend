package com.goldsilverratio.service.impl;

import com.goldsilverratio.entity.FuelInventory;
import com.goldsilverratio.entity.SilverInventory;
import com.goldsilverratio.mapper.FuelInventoryMapper;
import com.goldsilverratio.mapper.SilverInventoryMapper;
import com.goldsilverratio.service.ShfeInventoryFetcher;
import com.goldsilverratio.service.SilverInventoryApiService;
import com.goldsilverratio.service.ShfeSilverInventoryFetcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上期所白银库存 API 业务实现。获取该月数据时同时拉取白银与燃油库存。
 */
@Service
public class SilverInventoryApiServiceImpl implements SilverInventoryApiService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter RECORD_DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String PRODUCT_AG = "ag";
    private static final String PRODUCT_FU = "fu";

    private final SilverInventoryMapper silverInventoryMapper;
    private final FuelInventoryMapper fuelInventoryMapper;
    private final ShfeSilverInventoryFetcher shfeSilverInventoryFetcher;
    private final ShfeInventoryFetcher shfeInventoryFetcher;

    public SilverInventoryApiServiceImpl(SilverInventoryMapper silverInventoryMapper,
                                         FuelInventoryMapper fuelInventoryMapper,
                                         ShfeSilverInventoryFetcher shfeSilverInventoryFetcher,
                                         ShfeInventoryFetcher shfeInventoryFetcher) {
        this.silverInventoryMapper = silverInventoryMapper;
        this.fuelInventoryMapper = fuelInventoryMapper;
        this.shfeSilverInventoryFetcher = shfeSilverInventoryFetcher;
        this.shfeInventoryFetcher = shfeInventoryFetcher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveByDate(String dateStr, BigDecimal inventoryKg) {
        if (inventoryKg == null) {
            throw new IllegalArgumentException("inventoryKg required");
        }
        LocalDate recordDate = LocalDate.parse(dateStr, YYYYMMDD);
        silverInventoryMapper.deleteByDate(recordDate);
        SilverInventory record = new SilverInventory();
        record.setRecordDate(recordDate);
        record.setInventoryKg(inventoryKg);
        silverInventoryMapper.insert(record);
    }

    @Override
    public List<Map<String, Object>> listPage(int page, int size) {
        int offset = (page <= 0 ? 0 : page - 1) * (size <= 0 ? 100 : size);
        int limit = size <= 0 ? 100 : Math.min(size, 500);
        List<SilverInventory> list = silverInventoryMapper.selectPage(offset, limit);
        return buildListWithChange(list);
    }

    @Override
    public List<Map<String, Object>> listByMonth(int year, int month) {
        List<SilverInventory> list = silverInventoryMapper.selectByMonth(year, month);
        return buildListWithChange(list);
    }

    private List<Map<String, Object>> buildListWithChange(List<SilverInventory> list) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (SilverInventory r : list) {
            Map<String, Object> row = new HashMap<>(3);
            row.put("recordDate",
                    r.getRecordDate() == null ? "" : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("inventoryKg", r.getInventoryKg());
            BigDecimal change = computeChangeFromPrev(r.getRecordDate(), r.getInventoryKg());
            row.put("changeFromPrev", change);
            result.add(row);
        }
        return result;
    }

    private BigDecimal computeChangeFromPrev(LocalDate recordDate, BigDecimal current) {
        if (recordDate == null || current == null) {
            return null;
        }
        SilverInventory prev = silverInventoryMapper.selectPrevByDate(recordDate);
        if (prev == null || prev.getInventoryKg() == null) {
            return null;
        }
        return current.subtract(prev.getInventoryKg());
    }

    @Override
    public String fetchFromShfe(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return "日期格式须为 yyyyMMdd";
        }
        LocalDate date = LocalDate.parse(dateStr, YYYYMMDD);
        BigDecimal inventory = shfeSilverInventoryFetcher.fetchByDate(date);
        if (inventory == null) {
            return "该日无数据或解析失败，请检查是否为交易日";
        }
        saveByDate(dateStr, inventory);
        return "已获取并保存：" + inventory + " kg";
    }

    @Override
    public String fetchMonthFromShfe(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        int lastDay = ym.lengthOfMonth();
        int silverSaved = 0;
        int fuelSaved = 0;
        for (int day = 1; day <= lastDay; day++) {
            LocalDate d = ym.atDay(day);
            BigDecimal agKg = shfeInventoryFetcher.fetchByDate(d, PRODUCT_AG);
            if (agKg != null) {
                saveByDate(d.format(YYYYMMDD), agKg);
                silverSaved++;
            }
            BigDecimal fuKg = shfeInventoryFetcher.fetchByDate(d, PRODUCT_FU);
            if (fuKg != null) {
                saveFuelByDate(d, fuKg);
                fuelSaved++;
            }
        }
        if (silverSaved == 0 && fuelSaved == 0) {
            return "该月无有效数据或上期所未返回数据";
        }
        return "白银 " + silverSaved + " 条，燃油 " + fuelSaved + " 条";
    }

    private void saveFuelByDate(LocalDate recordDate, BigDecimal inventoryKg) {
        if (inventoryKg == null) {
            return;
        }
        fuelInventoryMapper.deleteByDate(recordDate);
        FuelInventory record = new FuelInventory();
        record.setRecordDate(recordDate);
        record.setInventoryKg(inventoryKg);
        fuelInventoryMapper.insert(record);
    }

    @Override
    public List<Map<String, Object>> getDetailFromShfe(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return null;
        }
        LocalDate date = LocalDate.parse(dateStr, YYYYMMDD);
        return shfeSilverInventoryFetcher.fetchDetailByDate(date);
    }
}
