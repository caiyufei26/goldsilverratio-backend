package com.goldsilverratio.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldsilverratio.service.RatioUsdApiService;
import com.goldsilverratio.service.RatioUsdFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * 从 GoldAPI.io 拉取 XAU/USD、XAG/USD 并保存为美元计价金银比。
 * 支持按日期拉取（含历史），按月份则逐日请求。
 */
@Service
public class RatioUsdFetcherImpl implements RatioUsdFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(RatioUsdFetcherImpl.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String GOLDAPI_BASE = "https://www.goldapi.io/api";
    private static final String XAU_USD = "XAU/USD";
    private static final String XAG_USD = "XAG/USD";

    @Value("${app.goldapi.token:}")
    private String goldApiToken;

    private final RatioUsdApiService ratioUsdApiService;

    public RatioUsdFetcherImpl(RatioUsdApiService ratioUsdApiService) {
        this.ratioUsdApiService = ratioUsdApiService;
    }

    @Override
    public String fetchAndSave(String dateStr) {
        LocalDate date;
        if (dateStr == null || dateStr.trim().isEmpty()) {
            date = LocalDate.now();
        } else {
            try {
                date = LocalDate.parse(dateStr.trim(), YYYYMMDD);
            } catch (Exception e) {
                return "日期格式错误，应为 yyyyMMdd";
            }
        }
        String dateParam = date.format(YYYYMMDD);

        if (goldApiToken == null || goldApiToken.isEmpty()) {
            return "未配置 app.goldapi.token，请在 application.yml 或环境变量 GOLDAPI_TOKEN 中设置";
        }

        try {
            BigDecimal gold = fetchPrice(XAU_USD, dateParam);
            BigDecimal silver = fetchPrice(XAG_USD, dateParam);
            if (gold == null || silver == null || silver.compareTo(BigDecimal.ZERO) <= 0) {
                return "GoldAPI 该日无有效金价/银价";
            }
            ratioUsdApiService.saveByDate(gold, silver, dateParam);
            LOG.info("美元计价金银比已保存: {} 金={} 银={} USD/oz", dateParam, gold, silver);
            return "已保存 " + dateParam;
        } catch (Exception e) {
            LOG.warn("美元计价金银比拉取失败: {}", e.getMessage());
            return "拉取失败: " + e.getMessage();
        }
    }

    @Override
    public String fetchMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) {
            end = today;
        }
        int ok = 0;
        int fail = 0;
        LocalDate day = start;
        while (!day.isAfter(end)) {
            String dateStr = day.format(YYYYMMDD);
            String msg = fetchAndSave(dateStr);
            if (msg != null && msg.startsWith("已保存")) {
                ok++;
            } else {
                fail++;
            }
            day = day.plusDays(1);
        }
        return String.format("已保存 %d 天，%d 失败", ok, fail);
    }

    /**
     * 请求 GoldAPI：GET /api/{metal}/{currency}/{date}，取 response.price 或 ask。
     */
    private BigDecimal fetchPrice(String metalCurrency, String dateYyyyMmDd) throws Exception {
        String urlStr = GOLDAPI_BASE + "/" + metalCurrency + "/" + dateYyyyMmDd;
        String json = getWithToken(urlStr);
        if (json == null || json.isEmpty()) {
            return null;
        }
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json);
        double price = root.has("price") && !root.path("price").isMissingNode()
                ? root.get("price").asDouble()
                : root.path("ask").asDouble(0);
        if (price <= 0) {
            return null;
        }
        return BigDecimal.valueOf(price);
    }

    private String getWithToken(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-access-token", goldApiToken);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; GoldSilverRatio/1.0)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (conn instanceof HttpsURLConnection) {
                try {
                    SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    ((HttpsURLConnection) conn).setSSLSocketFactory(ssf);
                } catch (Exception e) {
                    LOG.debug("设置 SSLSocketFactory 使用默认");
                }
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                LOG.warn("GoldAPI 返回 {}: {}", code, urlStr);
                return null;
            }
            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next().trim() : "";
            }
        } catch (Exception e) {
            LOG.warn("GoldAPI 请求失败 {}: {}", urlStr, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
