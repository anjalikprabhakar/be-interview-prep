package org.example.shortener;

import jakarta.validation.Valid;
import org.example.shortener.dto.ShortLinkResponse;
import org.example.shortener.dto.ShortenRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class ShortLinkController {

    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortLinkResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortLinkResponse created = service.shorten(request);
        // The stats endpoint is the API resource that describes the new link.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}/stats").buildAndExpand(created.code()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
