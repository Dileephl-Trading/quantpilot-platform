package com.dileephl.marketscanner;

import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import com.dileephl.marketscanner.config.MarketSessionProperties;
import com.dileephl.marketscanner.config.ScannerProperties;
import com.dileephl.marketscanner.config.AutomatedExecutionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({KiteConnectProperties.class, MarketSessionProperties.class, ScannerProperties.class,
        AutomatedExecutionProperties.class})
public class MarketSignalScannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketSignalScannerApplication.class, args);
    }
}
