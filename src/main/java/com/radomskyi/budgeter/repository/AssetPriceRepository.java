package com.radomskyi.budgeter.repository;

import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetPriceRepository extends JpaRepository<AssetPrice, Long> {

    boolean existsByAssetIdAndPriceDate(Long assetId, LocalDate priceDate);

    // Latest stored close on/before a date — used by net worth for current valuation
    Optional<AssetPrice> findFirstByAssetIdAndPriceDateLessThanEqualOrderByPriceDateDesc(
            Long assetId, LocalDate priceDate);

    // Most recent stored date per asset — sync fetches only what is missing
    Optional<AssetPrice> findFirstByAssetIdOrderByPriceDateDesc(Long assetId);

    // Bulk load for the portfolio history replay
    @Query("SELECT ap FROM AssetPrice ap WHERE ap.asset.id IN :assetIds")
    List<AssetPrice> findAllByAssetIds(@Param("assetIds") List<Long> assetIds);
}
