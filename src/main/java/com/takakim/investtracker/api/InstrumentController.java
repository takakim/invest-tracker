package com.takakim.investtracker.api;

import com.takakim.investtracker.service.InstrumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {
    private final InstrumentService service;
    public InstrumentController(InstrumentService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.InstrumentResponse create(@Valid @RequestBody ApiDtos.InstrumentRequest request) { return service.create(request); }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ApiDtos.InstrumentResponse update(@PathVariable UUID id, @Valid @RequestBody ApiDtos.InstrumentRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    public List<ApiDtos.InstrumentResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public ApiDtos.InstrumentResponse get(@PathVariable UUID id) { return service.get(id); }
}
