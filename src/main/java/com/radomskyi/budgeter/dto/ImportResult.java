package com.radomskyi.budgeter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a file import")
public class ImportResult {

    @Schema(description = "Number of records created")
    private int imported;

    @Schema(description = "Number of records skipped because they were already imported")
    private int skippedDuplicates;

    @Schema(description = "Number of rows skipped because they are not importable (cash movements, transfers, etc.)")
    private int skippedRows;

    @Schema(description = "Number of rows that failed to parse")
    private int failedRows;

    public void incrementImported() {
        imported++;
    }

    public void incrementSkippedDuplicates() {
        skippedDuplicates++;
    }

    public void incrementSkippedRows() {
        skippedRows++;
    }

    public void incrementFailedRows() {
        failedRows++;
    }
}
