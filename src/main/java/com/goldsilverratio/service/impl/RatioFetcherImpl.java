package com.goldsilverratio.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldsilverratio.config.NoSniSSLSocketFactory;
import com.goldsilverratio.service.RatioApiService;
import com.goldsilverratio.service.RatioFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 从 GoldAPI 拉取指定日期金价、银价并保存为金银比。
 * 使用 NoSniSSLSocketFactory 避免 Java 访问 goldapi.io 时的 SSL 握手失败。
 */
@Service
public class RatioFetcherImpl implements RatioFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(RatioFetcherImpl.class);
    private static final String GOLDAPI_BASE = "https://www.goldapi.io/api";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SSLSocketFactory NO_SNI_SSL =
            new NoSniSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());

    private final RatioApiService ratioApiService;
    private final ObjectMapper objectMapper;

    @Value("${app.goldapi.token:}")
    private String goldapiToken;

    public RatioFetcherImpl(RatioApiService ratioApiService, ObjectMapper objectMapper) {
        this.ratioApiService = ratioApiService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String fetchAndSave(String dateStr) {
        if (goldapiToken == null || goldapiToken.trim().isEmpty()) {
            return "未配置 GoldAPI Token（请设置 app.goldapi.token 或 GOLDAPI_TOKEN）";
        }
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
        LocalDate tradeDate = toTradeDate(date);
        String tradeDateStr = tradeDate.format(YYYYMMDD);

        String goldUrl = GOLDAPI_BASE + "/XAU/USD/" + tradeDateStr;
        String silverUrl = GOLDAPI_BASE + "/XAG/USD/" + tradeDateStr;

        try {
            BigDecimal gold = fetchPrice(goldUrl);
            BigDecimal silver = fetchPrice(silverUrl);
            if (gold == null || silver == null || silver.compareTo(BigDecimal.ZERO) <= 0) {
                return "GoldAPI 返回中未解析到有效金价/银价";
            }
            ratioApiService.saveByDate(gold, silver, tradeDateStr);
            LOG.info("金银比已拉取并保存: {} 金={} 银={}", tradeDateStr, gold, silver);
            return "已保存 " + tradeDateStr;
        } catch (Exception e) {
            LOG.warn("金银比拉取失败: {}", e.getMessage());
            return "拉取失败: " + e.getMessage();
        }
    }

    private static LocalDate toTradeDate(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return d.minusDays(2);
        }
        if (dow == DayOfWeek.SATURDAY) {
            return d.minusDays(1);
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal fetchPrice(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(NO_SNI_SSL);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("x-access-token", goldapiToken);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(35000);
        Map<String, Object> body;
        try (InputStream in = conn.getInputStream()) {
            body = objectMapper.readValue(in, Map.class);
        } finally {
            conn.disconnect();
        }
        if (body == null) {
            return null;
        }
        Object v = body.get("price");
        if (v == null) {
            v = body.get("close");
        }
        if (v == null) {
            v = body.get("ask");
        }
        if (v == null) {
            v = body.get("last");
        }
        if (v == null) {
            v = body.get("rate");
        }
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
