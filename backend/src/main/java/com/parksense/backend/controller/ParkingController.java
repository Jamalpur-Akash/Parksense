// package com.parksense.backend.controller;

// import tools.jackson.databind.JsonNode;
// import tools.jackson.databind.ObjectMapper;
// import org.springframework.web.bind.annotation.*;

// import java.net.URI;
// import java.net.URLEncoder;
// import java.net.http.HttpClient;
// import java.net.http.HttpRequest;
// import java.net.http.HttpResponse;
// import java.nio.charset.StandardCharsets;
// import java.time.Duration;
// import java.util.ArrayList;
// import java.util.Comparator;
// import java.util.List;

// @RestController
// @RequestMapping("/parking")
// //@CrossOrigin(origins = "http://localhost:5173")
// public class ParkingController {

//     private final HttpClient httpClient = HttpClient.newBuilder()
//             .connectTimeout(Duration.ofSeconds(15))
//             .build();
//     private final ObjectMapper mapper = new ObjectMapper();

//     private static final String[] OVERPASS_MIRRORS = {
//         "https://overpass-api.de/api/interpreter",
//         "https://overpass.kumi.systems/api/interpreter",
//         "https://lz4.overpass-api.de/api/interpreter"
//     };

//     @GetMapping("/nearby")
//     public List<ParkingSpot> nearbyParking(@RequestParam double lat,
//                                             @RequestParam double lng,
//                                             @RequestParam(defaultValue = "2000") int radiusMeters) {
//         String query = """
//             [out:json][timeout:25];
//             (
//               node["amenity"="parking"](around:%d,%f,%f);
//               way["amenity"="parking"](around:%d,%f,%f);
//             );
//             out center;
//             """.formatted(radiusMeters, lat, lng, radiusMeters, lat, lng);

//         Exception lastError = null;

//         for (String mirror : OVERPASS_MIRRORS) {
//             try {
//                 HttpRequest request = HttpRequest.newBuilder()
//                         .uri(URI.create(mirror))
//                         .header("Content-Type", "application/x-www-form-urlencoded")
//                         .header("User-Agent", "ParkSense/1.0 (student final-year project; no-parking-zone alert app)")
//                         .POST(HttpRequest.BodyPublishers.ofString(
//                                 "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
//                         .build();

//                 HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

//                 if (response.statusCode() != 200) {
//                     lastError = new RuntimeException(mirror + " returned HTTP " + response.statusCode());
//                     continue;
//                 }

//                 String body = response.body();
//                 if (body == null || body.isBlank() || body.trim().startsWith("<")) {
//                     lastError = new RuntimeException(mirror + " returned non-JSON (rate-limited or blocked)");
//                     continue;
//                 }

//                 JsonNode root = mapper.readTree(body);
//                 JsonNode elements = root.path("elements");

//                 List<ParkingSpot> spots = new ArrayList<>();
//                 for (JsonNode el : elements) {
//                     double spotLat, spotLng;
//                     if (el.has("lat")) {
//                         spotLat = el.get("lat").asDouble();
//                         spotLng = el.get("lon").asDouble();
//                     } else if (el.has("center")) {
//                         spotLat = el.get("center").get("lat").asDouble();
//                         spotLng = el.get("center").get("lon").asDouble();
//                     } else {
//                         continue;
//                     }
//                     String name = el.path("tags").path("name").asText("Unnamed Parking Area");
//                     double distance = distanceMeters(lat, lng, spotLat, spotLng);
//                     spots.add(new ParkingSpot(name, spotLat, spotLng, distance));
//                 }

//                 spots.sort(Comparator.comparingDouble(ParkingSpot::distanceMeters));
//                 return spots.size() > 15 ? spots.subList(0, 15) : spots;

//             } catch (Exception e) {
//                 lastError = e;
//                 System.err.println("Overpass mirror failed: " + mirror
//                         + " | Exception type: " + e.getClass().getName()
//                         + " | Message: " + e.getMessage());
//             }
//         }

//         throw new RuntimeException("Could not fetch nearby parking from any Overpass mirror: "
//                 + (lastError != null ? lastError.getMessage() : "unknown error"));
//     }

//     private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
//         double R = 6371000;
//         double dLat = Math.toRadians(lat2 - lat1);
//         double dLng = Math.toRadians(lng2 - lng1);
//         double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
//                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
//                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
//         return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//     }

//     public record ParkingSpot(String name, double lat, double lng, double distanceMeters) {}
// }




//new code

package com.parksense.backend.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/parking")
//@CrossOrigin(origins = "http://localhost:5173")
public class ParkingController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${geoapify.api.key}")
    private String geoapifyApiKey;

    @GetMapping("/nearby")
    public List<ParkingSpot> nearbyParking(@RequestParam double lat,
                                            @RequestParam double lng,
                                            @RequestParam(defaultValue = "2000") int radiusMeters) {
        try {
            String url = String.format(
                "https://api.geoapify.com/v2/places?categories=parking&filter=circle:%f,%f,%d&limit=20&apiKey=%s",
                lng, lat, radiusMeters, URLEncoder.encode(geoapifyApiKey, StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ParkSense/1.0 (student final-year project)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Geoapify returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode features = root.path("features");

            List<ParkingSpot> spots = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode props = feature.path("properties");
                JsonNode coords = feature.path("geometry").path("coordinates"); // [lon, lat]

                double spotLng = coords.size() > 0 ? coords.get(0).asDouble() : props.path("lon").asDouble();
                double spotLat = coords.size() > 1 ? coords.get(1).asDouble() : props.path("lat").asDouble();

                String name = (props.has("name") && !props.path("name").isNull())
                        ? props.path("name").asText()
                        : props.path("address_line1").asText("Unnamed Parking Area");

                double distance = distanceMeters(lat, lng, spotLat, spotLng);
                spots.add(new ParkingSpot(name, spotLat, spotLng, distance));
            }

            spots.sort(Comparator.comparingDouble(ParkingSpot::distanceMeters));
            return spots.size() > 15 ? spots.subList(0, 15) : spots;

        } catch (Exception e) {
            throw new RuntimeException("Could not fetch nearby parking: " + e.getMessage(), e);
        }
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record ParkingSpot(String name, double lat, double lng, double distanceMeters) {}
}