package com.parksense.backend.controller;

import com.parksense.backend.model.Zone;
import com.parksense.backend.repository.ZoneRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class LocationController {

    private final ZoneRepository zoneRepository;

    public LocationController(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @PostMapping("/check-location")
    public Map<String, Object> checkLocation(@RequestBody LocationRequest request) {
        List<Zone> zones = zoneRepository.findZonesContainingPoint(request.lng(), request.lat());

        if (zones.isEmpty()) {
            return Map.of("inZone", false);
        } else {
            Zone zone = zones.get(0);
            return Map.of(
                "inZone", true,
                "zoneName", zone.getName(),
                "verified", "approved".equals(zone.getStatus())
            );
        }
    }

    public record LocationRequest(double lat, double lng) {}
}