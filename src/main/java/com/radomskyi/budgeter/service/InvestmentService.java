package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.investment.*;
import com.radomskyi.budgeter.domain.service.InvestmentServiceInterface;
import com.radomskyi.budgeter.dto.InvestmentTransactionRequest;
import com.radomskyi.budgeter.exception.InvestmentTransactionNotFoundException;
import com.radomskyi.budgeter.repository.AssetRepository;
import com.radomskyi.budgeter.repository.InvestmentRepository;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvestmentService implements InvestmentServiceInterface {

    private final InvestmentTransactionRepository investmentTransactionRepository;
    private final InvestmentRepository investmentRepository;
    private final AssetRepository assetRepository;

    /** Create a new investment transaction */
    @Override
    @Transactional
    public InvestmentTransaction create(InvestmentTransactionRequest request) {
        log.info(
                "Creating new investment transaction for asset: {} with amount: {}",
                request.getAssetName(),
                request.getUnits().multiply(request.getPricePerUnit()));

        // Find or create asset
        Asset asset = findOrCreateAsset(request);

        // Find or create investment for this asset
        Investment investment = investmentRepository.findByAsset(asset).orElseGet(() -> {
            Investment newInvestment = Investment.createNew(asset, request.getCurrency(), request.getBrokerage());
            return investmentRepository.save(newInvestment);
        });

        // Create investment transaction
        InvestmentTransaction transaction = InvestmentTransaction.builder()
                .transactionType(request.getTransactionType())
                .investment(investment)
                .units(request.getUnits())
                .pricePerUnit(request.getPricePerUnit())
                .fees(request.getFees())
                .currency(request.getCurrency())
                .exchangeRate(request.getExchangeRate())
                .name(request.getName())
                .description(request.getDescription())
                .externalId(request.getExternalId())
                .transactionDate(request.getTransactionDate())
                .build();

        // Add transaction to investment (this updates all metrics)
        investment.addTransaction(transaction);

        // Save the investment (which cascades to save the transaction)
        Investment savedInvestment = investmentRepository.save(investment);

        // Get the saved transaction from the investment
        InvestmentTransaction savedTransaction = savedInvestment.getTransactions().stream()
                .filter(t -> t.getUnits().equals(request.getUnits())
                        && t.getPricePerUnit().equals(request.getPricePerUnit()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to retrieve saved transaction"));

        log.info("Successfully created investment transaction with id: {}", savedTransaction.getId());

        return savedTransaction;
    }

    /** Get investment transaction by ID */
    @Override
    public InvestmentTransaction getById(Long id) {
        log.info("Fetching investment transaction with id: {}", id);

        return investmentTransactionRepository
                .findById(id)
                .orElseThrow(() ->
                        new InvestmentTransactionNotFoundException("Investment transaction not found with id: " + id));
    }

    /** Get all investment transactions with pagination */
    @Override
    public Page<InvestmentTransaction> getAll(Pageable pageable) {
        log.info("Fetching all investment transactions with pagination: {}", pageable);

        return investmentTransactionRepository.findAll(pageable);
    }

    /** Update an existing investment transaction */
    @Override
    @Transactional
    public InvestmentTransaction update(Long id, InvestmentTransactionRequest request) {
        log.info("Updating investment transaction with id: {}", id);

        InvestmentTransaction existingTransaction = investmentTransactionRepository
                .findById(id)
                .orElseThrow(() ->
                        new InvestmentTransactionNotFoundException("Investment transaction not found with id: " + id));

        // Get the current investment
        Investment currentInvestment = existingTransaction.getInvestment();

        // Find or create asset if ticker/name changed
        Asset asset = findOrCreateAsset(request);

        // Check if asset changed (need to move to different investment)
        if (!currentInvestment.getAsset().getId().equals(asset.getId())) {
            // Remove from old investment
            currentInvestment.removeTransaction(existingTransaction);
            investmentRepository.save(currentInvestment);

            // Find or create new investment
            Investment newInvestment = investmentRepository.findByAsset(asset).orElseGet(() -> {
                Investment inv = Investment.createNew(asset, request.getCurrency(), request.getBrokerage());
                return investmentRepository.save(inv);
            });

            existingTransaction.setInvestment(newInvestment);
        }

        existingTransaction.setTransactionType(request.getTransactionType());
        existingTransaction.setUnits(request.getUnits());
        existingTransaction.setPricePerUnit(request.getPricePerUnit());
        existingTransaction.setFees(request.getFees());
        existingTransaction.setCurrency(request.getCurrency());
        existingTransaction.setExchangeRate(request.getExchangeRate());
        existingTransaction.setName(request.getName());
        existingTransaction.setDescription(request.getDescription());

        // Recalculate amount based on updated data
        recalculateAmount(existingTransaction);

        InvestmentTransaction updatedTransaction = investmentTransactionRepository.save(existingTransaction);
        log.info("Successfully updated investment transaction with id: {}", updatedTransaction.getId());

        return updatedTransaction;
    }

    /** Delete an investment transaction by ID */
    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting investment transaction with id: {}", id);

        if (!investmentTransactionRepository.existsById(id)) {
            throw new InvestmentTransactionNotFoundException("Investment transaction not found with id: " + id);
        }

        investmentTransactionRepository.deleteById(id);
        log.info("Successfully deleted investment transaction with id: {}", id);
    }

    /** Find existing asset or create a new one */
    private Asset findOrCreateAsset(InvestmentTransactionRequest request) {
        Asset asset = null;

        // Try to find by ISIN first (most reliable identifier)
        if (request.getAssetIsin() != null && !request.getAssetIsin().trim().isEmpty()) {
            asset = assetRepository.findByIsin(request.getAssetIsin()).orElse(null);
        }

        // If not found by ISIN, try by ticker
        if (asset == null
                && request.getAssetTicker() != null
                && !request.getAssetTicker().trim().isEmpty()) {
            asset = assetRepository.findByTicker(request.getAssetTicker()).orElse(null);
        }

        // If asset doesn't exist, create a new one
        if (asset == null) {
            asset = Asset.builder()
                    .ticker(request.getAssetTicker())
                    .name(request.getAssetName())
                    .isin(request.getAssetIsin())
                    .assetType(request.getAssetType() != null ? request.getAssetType() : AssetType.STOCK)
                    .investmentStyle(InvestmentStyle.GROWTH) // Default, could be improved
                    .build();

            asset = assetRepository.save(asset);
            log.info("Created new asset: {} ({})", asset.getName(), asset.getTicker());
        }

        return asset;
    }

    /** Recalculate the amount for an investment transaction */
    private void recalculateAmount(InvestmentTransaction transaction) {
        transaction.calculateAmount();
    }
}
