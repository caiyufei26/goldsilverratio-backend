package com.goldsilverratio.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从上期所官网解析白银仓单日报 HTML 数据。
 */
@Component
public class ShfeSilverInventoryFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(ShfeSilverInventoryFetcher.class);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 白银仓单 HTML：dailystock_{yyyyMMdd}/ZH/shfe/ag.html */
    private static final String HTML_BASE =
            "https://www.shfe.com.cn/data/tradedata/future/stockdata/dailystock_%s/ZH/shfe/ag.html";

    /**
     * 获取指定日期的白银库存（千克）。无数据或解析失败返回 null。
     *
     * @param date 日期
     * @return 库存千克数，或 null
     */
    public BigDecimal fetchByDate(LocalDate date) {
        String dateStr = date.format(YYYYMMDD);
        Document doc = fetchDocument(dateStr);
        return doc != null ? parseInventoryFromDocument(doc, dateStr) : null;
    }

    /**
     * 获取指定日期的白银库存分仓库明细。返回 [{ region, warehouse, futuresKg, changeKg, rowType }]。
     * rowType: data | subtotal | total
     */
    public List<Map<String, Object>> fetchDetailByDate(LocalDate date) {
        String dateStr = date.format(YYYYMMDD);
        Document doc = fetchDocument(dateStr);
        return doc != null ? parseDetailFromDocument(doc) : null;
    }

    private Document fetchDocument(String dateStr) {
        String url = String.format(HTML_BASE, dateStr) + "?params=" + System.currentTimeMillis();
        try {
            Connection conn = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .ignoreContentType(true);
            return conn.get();
        } catch (Exception e) {
            LOG.warn("Fetch SHFE silver inventory failed for {}: {}", dateStr, e.getMessage());
            return null;
        }
    }

    /**
     * 从 HTML 文档解析库存总数。优先查找「总计」行（总合计），否则按「期货」列求和。
     */
    private BigDecimal parseInventoryFromDocument(Document doc, String dateStr) {
        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            BigDecimal total = null;
            for (Element row : rows) {
                String rowText = row.text();
                if (rowText.contains("总计")) {
                    total = extractNumberFromRow(row);
                    if (total != null) {
                        return total;
                    }
                }
            }
            total = sumFuturesColumn(rows);
            if (total != null) {
                return total;
            }
        }
        return tryExtractFromText(doc.html(), dateStr);
    }

    /**
     * 解析分仓库明细。表头：地区、仓库、期货、增减。
     */
    private List<Map<String, Object>> parseDetailFromDocument(Document doc) {
        List<Map<String, Object>> result = new ArrayList<>();
        Elements tables = doc.select("table");
        int regionCol = -1;
        int warehouseCol = -1;
        int futuresCol = -1;
        int changeCol = -1;
        String lastRegion = "";
        for (Element table : tables) {
            Elements rows = table.select("tr");
            boolean foundHeader = false;
            for (Element row : rows) {
                Elements cells = row.select("td, th");
                if (cells.isEmpty()) {
                    continue;
                }
                if (!foundHeader) {
                    for (int i = 0; i < cells.size(); i++) {
                        String h = cells.get(i).text().trim();
                        if ("地区".equals(h)) {
                            regionCol = i;
                        } else if ("仓库".equals(h)) {
                            warehouseCol = i;
                        } else if ("期货".equals(h) || h.contains("今日注册仓单")) {
                            futuresCol = i;
                        } else if ("增减".equals(h)) {
                            changeCol = i;
                        }
                    }
                    if (regionCol >= 0 && warehouseCol >= 0 && futuresCol >= 0 && changeCol >= 0) {
                        foundHeader = true;
                    }
                    continue;
                }
                String region = getCellText(cells, regionCol);
                String warehouse = getCellText(cells, warehouseCol);
                BigDecimal futures = parseNumber(getCellText(cells, futuresCol).replace(",", ""));
                BigDecimal change = parseNumber(getCellText(cells, changeCol).replace(",", ""));
                if (!region.isEmpty()) {
                    lastRegion = region;
                }
                if (warehouse.isEmpty() && futures == null && change == null) {
                    continue;
                }
                if ("地区".equals(warehouse) || "仓库".equals(warehouse) || "期货".equals(warehouse)
                        || "增减".equals(warehouse)) {
                    continue;
                }
                String rowType = "data";
                if ("合计".equals(warehouse)) {
                    rowType = "subtotal";
                } else if ("总计".equals(warehouse)) {
                    rowType = "total";
                }
                Map<String, Object> item = new HashMap<>(5);
                item.put("region", "total".equals(rowType) ? "" : lastRegion);
                item.put("warehouse", warehouse);
                item.put("futuresKg", futures);
                item.put("changeKg", change);
                item.put("rowType", rowType);
                result.add(item);
            }
            if (!result.isEmpty()) {
                break;
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String getCellText(Elements cells, int idx) {
        if (idx < 0 || idx >= cells.size()) {
            return "";
        }
        return cells.get(idx).text().replace(",", "").replace("，", "").trim();
    }

    private BigDecimal extractNumberFromRow(Element row) {
        Elements cells = row.select("td");
        if (cells.isEmpty()) {
            cells = row.select("th");
        }
        for (int i = 1; i < cells.size(); i++) {
            String text = cells.get(i).text().replace(",", "").replace("，", "").trim();
            BigDecimal n = parseNumber(text);
            if (n != null && n.compareTo(BigDecimal.ZERO) > 0) {
                return n;
            }
        }
        return null;
    }

    /** 按「期货」列求和（该页白银单位已是千克）。 */
    private BigDecimal sumFuturesColumn(Elements rows) {
        int futuresColIdx = -1;
        BigDecimal sum = BigDecimal.ZERO;
        boolean foundHeader = false;
        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.isEmpty()) {
                continue;
            }
            if (!foundHeader) {
                for (int i = 0; i < cells.size(); i++) {
                    String h = cells.get(i).text();
                    if ("期货".equals(h) || h.contains("今日注册仓单")) {
                        futuresColIdx = i;
                        foundHeader = true;
                        break;
                    }
                }
                continue;
            }
            if (futuresColIdx >= 0 && futuresColIdx < cells.size()) {
                String text = cells.get(futuresColIdx).text().replace(",", "").trim();
                BigDecimal n = parseNumber(text);
                if (n != null && n.compareTo(BigDecimal.ZERO) >= 0) {
                    sum = sum.add(n);
                }
            }
        }
        return sum.compareTo(BigDecimal.ZERO) > 0 ? sum : null;
    }

    private BigDecimal tryExtractFromText(String html, String dateStr) {
        Pattern p = Pattern.compile("(\\d{1,3}(?:,\\d{3})*)\\.?\\d*\\s*千克");
        Matcher m = p.matcher(html);
        if (m.find()) {
            String s = m.group(1).replace(",", "");
            return parseNumber(s);
        }
        p = Pattern.compile("总计[^\\d]*(\\d{1,3}(?:,\\d{3})*)");
        m = p.matcher(html);
        if (m.find()) {
            return parseNumber(m.group(1).replace(",", ""));
        }
        return null;
    }

    private BigDecimal parseNumber(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        s = s.replace(",", "").replace("，", "").trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
