package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.service.NetWorthServiceInterface;
import com.radomskyi.budgeter.dto.NetWorthPosition;
import com.radomskyi.budgeter.dto.NetWorthResponse;
import com.radomskyi.budgeter.repository.InvestmentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates net worth from imported investments. Position values are based on the price of the
 * most recent imported trade per asset (no live price feed), converted to EUR with the exchange
 * rate from that trade.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NetWorthService implements NetWorthServiceInterface {

    private final InvestmentRepository investmentRepository;

    @Override
    public NetWorthResponse getNetWorth() {
        log.info("Calculating net worth");

        List<Investment> openInvestments = investmentRepository.findAll().stream()
                .filter(investment -> investment.getTotalUnits() != null
                        && investment.getTotalUnits().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        Map<String, BigDecimal> byBrokerage = new TreeMap<>();
        Map<String, BigDecimal> byAssetType = new TreeMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        List<NetWorthPosition> positions = openInvestments.stream()
                .map(this::toPosition)
                .sorted((a, b) -> b.getValueEur().compareTo(a.getValueEur()))
                .toList();

        for (NetWorthPosition position : positions) {
            totalValue = totalValue.add(position.getValueEur());
            String brokerage = position.getBrokerage() != null ? position.getBrokerage() : "Unknown";
            byBrokerage.merge(brokerage, position.getValueEur(), BigDecimal::add);
            byAssetType.merge(position.getAssetType().name(), position.getValueEur(), BigDecimal::add);
        }

        return NetWorthResponse.builder()
                .totalValue(totalValue)
                .currency("EUR")
                .byBrokerage(byBrokerage)
                .byAssetType(byAssetType)
                .positions(positions)
                .build();
    }

    private NetWorthPosition toPosition(Investment investment) {
        // Trade Republic securities are ISIN-only; fall back to ISIN so clients always have an id
        String ticker = investment.getAsset().getTicker() != null
                ? investment.getAsset().getTicker()
                : investment.getAsset().getIsin();
        return NetWorthPosition.builder()
                .ticker(ticker)
                .name(investment.getAsset().getName())
                .isin(investment.getAsset().getIsin())
                .assetType(investment.getAsset().getAssetType())
                .brokerage(investment.getBrokerage())
                .units(investment.getTotalUnits())
                .latestPrice(investment.getLatestPrice())
                .priceCurrency(investment.getCurrency())
                .valueEur(investment.getCurrentValueEur())
                .build();
    }
}
