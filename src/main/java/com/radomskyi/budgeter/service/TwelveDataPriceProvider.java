package com.radomskyi.budgeter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.radomskyi.budgeter.domain.service.PriceProviderInterface;
import com.radomskyi.budgeter.dto.DailyPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Twelve Data client (free tier: 800 credits/day, 8 requests/minute). Docs:
 * https://twelvedata.com/docs — /time_series for EOD closes, /symbol_search for ISIN resolution.
 * The only class that knows this HTTP API; everything else talks to PriceProviderInterface.
 */
@Service
@Slf4j
public class TwelveDataPriceProvider implements PriceProviderInterface {

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataPriceProvider(
            RestClient.Builder restClientBuilder,
            @Value("${pricesync.base-url:https://api.twelvedata.com}") String baseUrl,
            @Value("${pricesync.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<DailyPrice> getEodPrices(String symbol, LocalDate from, LocalDate to) {
        JsonNode response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1day")
                        .queryParam("start_date", from.toString())
                        .queryParam("end_date", to.toString())
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !"ok".equalsIgnoreCase(response.path("status").asText())) {
            // Twelve Data returns 200 with {"status":"error","message":...} for unknown symbols
            log.warn(
                    "Twelve Data time_series failed for {}: {}",
                    symbol,
                    response != null ? response.path("message").asText() : "empty response");
            return List.of();
        }

        List<DailyPrice> prices = new ArrayList<>();
        for (JsonNode value : response.path("values")) {
            prices.add(new DailyPrice(
                    LocalDate.parse(value.path("datetime").asText()),
                    new BigDecimal(value.path("close").asText())));
        }
        prices.sort(Comparator.comparing(DailyPrice::date));
        return prices;
    }

    @Override
    public Optional<String> findSymbolByIsin(String isin) {
        JsonNode response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/symbol_search")
                        .queryParam("symbol", isin)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("data").isEmpty()) {
            return Optional.empty();
        }
        // First match is the provider's best guess; the cached value can be corrected manually
        // via asset.price_symbol if it picks the wrong listing
        JsonNode best = response.path("data").get(0);
        String symbol = best.path("symbol").asText(null);
        String exchange = best.path("exchange").asText("");
        log.info("Resolved ISIN {} to symbol {} ({})", isin, symbol, exchange);
        return Optional.ofNullable(symbol);
    }

    @Override
    public String sourceName() {
        return "TWELVE_DATA";
    }
}
