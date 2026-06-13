package com.radomskyi.budgeter.domain.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "Banking documents import controller",
        description = "Responsible for importing files from apps like Trading212, Trade Republic, Finanzguru, etc.")
@RequestMapping("/api/import")
public interface ImportControllerInterface {

    @PostMapping("/trading212")
    @Operation(summary = "Import investment transactions from a Trading212 history CSV export")
    ResponseEntity<String> importTrading212(
            @Parameter(description = "Trading212 CSV file to import") @RequestParam("file") MultipartFile file);

    @PostMapping("/traderepublic")
    @Operation(summary = "Import investment transactions from a Trade Republic transaction CSV export")
    ResponseEntity<String> importTradeRepublic(
            @Parameter(description = "Trade Republic CSV file to import") @RequestParam("file") MultipartFile file);

    @PostMapping("/finanzguru")
    @Operation(summary = "Import expenses and incomes from a Finanzguru 'Alle Buchungen' XLSX export")
    ResponseEntity<String> importFinanzguru(
            @Parameter(description = "Finanzguru XLSX file to import") @RequestParam("file") MultipartFile file);
}
