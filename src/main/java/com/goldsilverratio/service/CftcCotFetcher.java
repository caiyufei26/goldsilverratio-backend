package com.goldsilverratio.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 CFTC 官网解析 COT 持仓报告（CMX 黄金、白银）。
 * 数据源：https://www.cftc.gov/dea/futures/deacmxsf.htm
 */
@Component
public class CftcCotFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CftcCotFetcher.class);

    private static final String CMX_URL = "https://www.cftc.gov/dea/futures/deacmxsf.htm";
    /** 直连失败时使用的 CORS 代理，绕过 TLS handshake_failure */
    private static final String CORS_PROXY = "https://corsproxy.io/?";
    private static final DateTimeFormatter REPORT_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    /**
     * 获取最新 COT 数据。返回 [{ commodityCode, recordDate, totalOpenInterest, fundLong, fundShort,
     * fundNet, commercialLong, commercialShort, reportableTraders }]。
     */
    public List<Map<String, Object>> fetchLatest() {
        List<Map<String, Object>> result = new ArrayList<>();
        String text = fetchHtml(CMX_URL);
        if (text != null) {
            parseCommodity(text, "GOLD", "GC", result);
            parseCommodity(text, "SILVER", "SI", result);
        }
        return result;
    }

    private String fetchHtml(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            return doc.body().text();
        } catch (Exception e) {
            LOG.warn("CFTC direct fetch failed: {}, trying CORS proxy", e.getMessage());
            try {
                String proxyUrl = CORS_PROXY + java.net.URLEncoder.encode(url, "UTF-8");
                Document doc = Jsoup.connect(proxyUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(20000)
                        .get();
                return doc.body().text();
            } catch (Exception ex) {
                LOG.warn("CFTC CORS proxy fetch failed: {}", ex.getMessage());
                return null;
            }
        }
    }

    private void parseCommodity(String text, String nameKey, String commodityCode,
                                List<Map<String, Object>> result) {
        String marker = nameKey + " - COMMODITY EXCHANGE";
        int idx = text.indexOf(marker);
        if (idx < 0) {
            return;
        }
        int end = text.indexOf(" - COMMODITY EXCHANGE", idx + marker.length());
        if (end < 0) {
            end = text.length();
        }
        String block = text.substring(idx, Math.min(idx + 1200, end));
        LocalDate recordDate = parseReportDate(block);
        if (recordDate == null) {
            return;
        }
        Long totalOi = parseOpenInterest(block);
        long[] commitments = parseCommitments(block);
        Integer totalTraders = parseTotalTraders(block);
        if (totalOi == null && commitments == null) {
            return;
        }
        Map<String, Object> row = new HashMap<>(10);
        row.put("commodityCode", commodityCode);
        row.put("recordDate", recordDate);
        row.put("date", recordDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        row.put("totalOpenInterest", totalOi);
        if (commitments != null && commitments.length >= 5) {
            long fundLong = commitments[0];
            long fundShort = commitments[1];
            row.put("fundLong", fundLong);
            row.put("fundShort", fundShort);
            row.put("fundNet", fundLong - fundShort);
            row.put("commercialLong", commitments[3]);
            row.put("commercialShort", commitments[4]);
        }
        row.put("reportableTraders", totalTraders);
        result.add(row);
    }

    private LocalDate parseReportDate(String block) {
        Pattern p = Pattern.compile("AS OF\\s+(\\d{2}/\\d{2}/\\d{2})");
        Matcher m = p.matcher(block);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1).trim(), REPORT_DATE);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Long parseOpenInterest(String block) {
        Pattern p = Pattern.compile("OPEN INTEREST:\\s*([\\d,]+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1).replace(",", ""));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private long[] parseCommitments(String block) {
        Pattern p = Pattern.compile("COMMITMENTS\\s+([\\d,\\s]+?)\\s+CHANGES");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String nums = m.group(1).replace(",", " ").trim();
            String[] parts = nums.split("\\s+");
            if (parts.length >= 5) {
                long[] arr = new long[9];
                for (int i = 0; i < Math.min(parts.length, 9); i++) {
                    try {
                        arr[i] = Long.parseLong(parts[i].trim());
                    } catch (NumberFormatException e) {
                        arr[i] = 0;
                    }
                }
                return arr;
            }
        }
        return null;
    }

    private Integer parseTotalTraders(String block) {
        Pattern p = Pattern.compile("TOTAL TRADERS:\\s*([\\d,]+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1).replace(",", ""));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
