package com.goldsilverratio.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldsilverratio.service.RatioUsdApiService;
import com.goldsilverratio.service.RatioUsdFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

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

        if (ratioUsdApiService.hasDataForDate(dateParam)) {
            LOG.debug("美元计价金银比已存在，跳过: {}", dateParam);
            return "已跳过 " + dateParam;
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
        int skip = 0;
        int fail = 0;
        LocalDate day = start;
        while (!day.isAfter(end)) {
            String dateStr = day.format(YYYYMMDD);
            String msg = fetchAndSave(dateStr);
            if (msg != null && msg.startsWith("已保存")) {
                ok++;
            } else if (msg != null && msg.startsWith("已跳过")) {
                skip++;
            } else {
                fail++;
            }
            day = day.plusDays(1);
        }
        return String.format("已保存 %d 天，跳过 %d 天，%d 失败", ok, skip, fail);
    }

    /**
     * 仅通过 curl 请求 GoldAPI，不 fallback 到 Java，避免阻塞与重试。
     */
    private BigDecimal fetchPrice(String metalCurrency, String dateYyyyMmDd) throws Exception {
        String urlStr = GOLDAPI_BASE + "/" + metalCurrency + "/" + dateYyyyMmDd;
        String json = getWithTokenViaCurl(urlStr);
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

    /**
     * 通过系统 curl 请求 GoldAPI，需本机已安装 curl（Windows 10+ 通常自带）。
     */
    private String getWithTokenViaCurl(String urlStr) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-f", "-m", "30",
                    "-H", "x-access-token: " + goldApiToken,
                    "-H", "User-Agent: Mozilla/5.0 (compatible; GoldSilverRatio/1.0)",
                    urlStr
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("GoldAPI curl 超时: {}", urlStr);
                return null;
            }
            if (process.exitValue() != 0) {
                LOG.warn("GoldAPI curl 退出码 {}: {}", process.exitValue(), urlStr);
                return null;
            }
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                return null;
            }
            LOG.info("GoldAPI 通过 curl 获取成功: {}", urlStr);
            return result;
        } catch (Exception e) {
            LOG.warn("GoldAPI curl 执行失败 {}: {}", urlStr, e.getMessage());
            return null;
        }
    }
}
