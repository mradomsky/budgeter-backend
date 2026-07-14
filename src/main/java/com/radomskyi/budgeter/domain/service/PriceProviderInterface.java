package com.radomskyi.budgeter.domain.service;

import com.radomskyi.budgeter.dto.DailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Market data provider abstraction — only implementations know the HTTP API. */
public interface PriceProviderInterface {

    /** EOD closes for the symbol in [from, to], oldest first. Empty when the symbol is unknown. */
    List<DailyPrice> getEodPrices(String symbol, LocalDate from, LocalDate to);

    /** Resolve a provider symbol from an ISIN, when the asset has no usable ticker. */
    Optional<String> findSymbolByIsin(String isin);

    /** Short identifier stored in asset_price.source, e.g. "TWELVE_DATA". */
    String sourceName();
}
