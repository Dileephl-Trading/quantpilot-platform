package com.dileephl.marketscanner.ingestion;

import com.dileephl.marketscanner.service.SignalEngine.Candle;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

/** Imports locally recorded candle files; no API key or internet connection is needed. */
public final class CsvCandleImporter {
    public List<Candle> read(Path path) throws IOException {
        try (var lines = Files.lines(path)) {
            return lines
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.toLowerCase().startsWith("timestamp,"))
                    .map(this::parse)
                    .toList();
        }
    }

    private Candle parse(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Expected CSV columns: timestamp,open,high,low,close,volume");
        }
        return new Candle(
                OffsetDateTime.parse(fields[0].trim()),
                new BigDecimal(fields[1].trim()),
                new BigDecimal(fields[2].trim()),
                new BigDecimal(fields[3].trim()),
                new BigDecimal(fields[4].trim()),
                Long.parseLong(fields[5].trim()));
    }
}
