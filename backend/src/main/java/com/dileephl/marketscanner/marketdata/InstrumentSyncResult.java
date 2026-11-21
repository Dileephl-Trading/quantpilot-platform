package com.dileephl.marketscanner.marketdata;

import java.time.OffsetDateTime;

public record InstrumentSyncResult(String provider, String scope, int savedInstruments, OffsetDateTime syncedAt) { }
