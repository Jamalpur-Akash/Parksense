package com.parksense.backend.controller;

import com.parksense.backend.repository.NearbyZoneMatch;
import com.parksense.backend.repository.PendingZone;
import com.parksense.backend.repository.ZoneRepository;
import com.parksense.backend.repository.ZoneSummary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@RestController
@RequestMapping("/zones")
@CrossOrigin(origins = "http://localhost:5173")
public class ZoneController {

    private static final double NEARBY_THRESHOLD_METERS = 20.0;
    private static final int CORROBORATION_THRESHOLD = 3;

    // Simple shared-secret gate for admin actions — an honest MVP simplification,
    // not real authentication. Fine to state as-is; real login is future scope.
    @Value("${admin.key}")
    private String ADMIN_KEY;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final ZoneRepository zoneRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public ZoneController(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @GetMapping
    public List<ZoneSummary> listZones() {
        return zoneRepository.findAllZoneSummaries();
    }

    @PostMapping("/report-with-photo")
    public Map<String, Object> reportZoneWithPhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("name") String name,
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "radiusMeters", required = false) Double radiusMeters,
            @RequestParam("reporterId") String reporterId) {

        double radius = radiusMeters != null ? radiusMeters : 15.0;

        String photoUrl;
        try {
            Path uploadDir = Paths.get("uploads");
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + "_" + photo.getOriginalFilename();
            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, photo.getBytes());
            photoUrl = "/uploads/" + filename;
        } catch (IOException e) {
            return Map.of("status", "error", "message", "Could not save photo: " + e.getMessage());
        }

        Map<?, ?> mlResult;
        try {
            ByteArrayResource fileResource = new ByteArrayResource(photo.getBytes()) {
                @Override
                public String getFilename() {
                    return photo.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    mlServiceUrl + "/detect-sign", requestEntity, Map.class);

            mlResult = response.getBody();
        } catch (Exception e) {
            return Map.of("status", "error", "message", "Could not reach ML service: " + e.getMessage());
        }

        if (mlResult == null) {
            return Map.of("status", "error", "message", "ML service returned no result");
        }

        int detections = ((Number) mlResult.get("detections")).intValue();

        // Is this photo reporting a spot that already has a user-reported zone nearby?
        Optional<NearbyZoneMatch> existing = zoneRepository.findNearbyUserReportedZone(lng, lat, NEARBY_THRESHOLD_METERS);

        Long zoneId;
        String zoneStatus;
        double confidence;

        if (existing.isPresent()) {
            zoneId = existing.get().getId();
            zoneStatus = existing.get().getStatus();

            // A previously rejected zone can be revived if THIS new photo actually
            // detected something — one bad/blurry earlier photo shouldn't permanently
            // block a real report from the same spot. The new photo replaces the old
            // one so the admin panel shows the photo that actually caused "pending".
            if ("rejected".equals(zoneStatus) && detections > 0) {
                zoneStatus = "pending";
                confidence = 0.5;
                zoneRepository.updateZoneStatusAndPhoto(zoneId, "pending", photoUrl);
            } else {
                confidence = switch (zoneStatus) {
                    case "approved" -> 1.0;
                    case "pending" -> 0.5;
                    default -> 0.0;
                };
            }
        } else {
            zoneStatus = detections > 0 ? "pending" : "rejected";
            confidence = detections > 0 ? 0.5 : 0.0;
            zoneRepository.insertUserReportedZoneWithVerification(name, lat, lng, radius, zoneStatus, confidence, photoUrl);
            zoneId = zoneRepository.findLastInsertedZoneId();
        }

        // Same reporter submitting again for the same zone is silently ignored here —
        // the UNIQUE constraint in zone_reports guarantees they can only ever count once.
        int rowsInserted = zoneRepository.insertZoneReport(zoneId, reporterId);
        boolean isNewReporter = rowsInserted > 0;

        long totalReporters = zoneRepository.countReportsForZone(zoneId);

        boolean justAutoApproved = false;
        if ("pending".equals(zoneStatus) && totalReporters >= CORROBORATION_THRESHOLD) {
            zoneRepository.updateZoneStatus(zoneId, "approved");
            zoneStatus = "approved";
            justAutoApproved = true;
        }

        return Map.of(
                "status", zoneStatus,
                "confidence", confidence,
                "detections", detections,
                "photoUrl", photoUrl,
                "reporterCounted", isNewReporter,
                "totalReporters", totalReporters,
                "autoApproved", justAutoApproved
        );
    }

    @GetMapping("/pending")
    public ResponseEntity<?> listPendingZones(@RequestParam("adminKey") String adminKey) {
        if (!ADMIN_KEY.equals(adminKey)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin key"));
        }
        List<PendingZone> pending = zoneRepository.findPendingZones();
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveZone(@PathVariable Long id, @RequestParam("adminKey") String adminKey) {
        if (!ADMIN_KEY.equals(adminKey)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin key"));
        }
        zoneRepository.updateZoneStatus(id, "approved");
        return ResponseEntity.ok(Map.of("status", "approved"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectZone(@PathVariable Long id, @RequestParam("adminKey") String adminKey) {
        if (!ADMIN_KEY.equals(adminKey)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin key"));
        }
        zoneRepository.updateZoneStatus(id, "rejected");
        return ResponseEntity.ok(Map.of("status", "rejected"));
    }
}