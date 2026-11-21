package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class TradingProfileController {
    private final TradingProfileService profiles;
    public TradingProfileController(TradingProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public TradingProfile current() { return profiles.current(); }

    @PutMapping
    public ResponseEntity<TradingProfile> update(@RequestBody TradingProfile profile) {
        return ResponseEntity.ok(profiles.update(profile));
    }
}
