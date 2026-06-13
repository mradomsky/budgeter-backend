package com.radomskyi.budgeter.controller;

import com.radomskyi.budgeter.domain.controller.ImportControllerInterface;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.service.FinanzguruImportService;
import com.radomskyi.budgeter.service.TradeRepublicImportService;
import com.radomskyi.budgeter.service.Trading212ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ImportController implements ImportControllerInterface {

    private final Trading212ImportService trading212ImportService;
    private final TradeRepublicImportService tradeRepublicImportService;
    private final FinanzguruImportService finanzguruImportService;

    @Override
    public ResponseEntity<String> importTrading212(MultipartFile file) {
        return runImport("Trading212", file, () -> trading212ImportService.importCsv(file));
    }

    @Override
    public ResponseEntity<String> importTradeRepublic(MultipartFile file) {
        return runImport("Trade Republic", file, () -> tradeRepublicImportService.importCsv(file));
    }

    @Override
    public ResponseEntity<String> importFinanzguru(MultipartFile file) {
        return runImport("Finanzguru", file, () -> finanzguruImportService.importXlsx(file));
    }

    private ResponseEntity<String> runImport(String source, MultipartFile file, ImportCall importCall) {
        log.info("Received request to import {} file: {}", source, file.getOriginalFilename());

        try {
            ImportResult result = importCall.run();
            String message = String.format(
                    "Imported %d records from %s file '%s' (%d duplicates skipped, %d rows not importable, %d failed)",
                    result.getImported(),
                    source,
                    file.getOriginalFilename(),
                    result.getSkippedDuplicates(),
                    result.getSkippedRows(),
                    result.getFailedRows());
            log.info(message);
            return ResponseEntity.ok(message);

        } catch (IllegalArgumentException e) {
            String errorMessage = "Failed to import " + source + " file: " + e.getMessage();
            log.error(errorMessage, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);

        } catch (Exception e) {
            String errorMessage = "Failed to import " + source + " file: " + e.getMessage();
            log.error(errorMessage, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }
    }

    @FunctionalInterface
    private interface ImportCall {
        ImportResult run() throws Exception;
    }
}
