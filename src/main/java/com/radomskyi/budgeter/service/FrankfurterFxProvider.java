package com.radomskyi.budgeter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.service.FxRateProviderInterface;
import com.radomskyi.budgeter.dto.DailyFxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * frankfurter.app client — ECB reference rates, free, no API key. Asks for EUR per 1 unit of the
 * foreign currency directly (from=CCY&to=EUR). GBX is not an ISO currency; callers convert via
 * GBP/100 and must not request it here.
 */
@Service
@Slf4j
public class FrankfurterFxProvider implements FxRateProviderInterface {

    private final RestClient restClient;

    public FrankfurterFxProvider(
            RestClient.Builder restClientBuilder,
            @Value("${pricesync.fx-base-url:https://api.frankfurter.app}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public List<DailyFxRate> getRatesToEur(Currency currency, LocalDate from, LocalDate to) {
        if (currency == Currency.EUR || currency == Currency.GBX) {
            throw new IllegalArgumentException("No direct ECB rate for " + currency);
        }

        JsonNode response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + from + ".." + to)
                        .queryParam("from", currency.name())
                        .queryParam("to", "EUR")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("rates").isEmpty()) {
            log.warn("Frankfurter returned no {}→EUR rates for {}..{}", currency, from, to);
            return List.of();
        }

        List<DailyFxRate> rates = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = response.path("rates").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            rates.add(new DailyFxRate(
                    LocalDate.parse(entry.getKey()),
                    new BigDecimal(entry.getValue().path("EUR").asText())));
        }
        rates.sort(Comparator.comparing(DailyFxRate::date));
        return rates;
    }
}
