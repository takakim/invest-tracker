package com.takakim.investtracker.api;

import com.takakim.investtracker.service.system.SystemService;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @PostMapping("/reset-database")
    public ResponseEntity<ApiDtos.DatabaseResetResponse> resetDatabase(
            @RequestBody(required = false) ApiDtos.DatabaseResetRequest request) {
        if (request == null || request.confirmation() == null || !request.confirmation().trim().equalsIgnoreCase("RESET")) {
            throw new IllegalArgumentException("Database reset requires confirmation parameter: 'RESET'");
        }

        systemService.resetDatabase();

        return ResponseEntity.ok(new ApiDtos.DatabaseResetResponse(
                "Database reset successfully. All portfolio data, transactions, and observations have been wiped.",
                Instant.now()
        ));
    }
}
