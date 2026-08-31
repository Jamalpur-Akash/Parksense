package com.parksense.backend.repository;

import com.parksense.backend.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    @Query(value = """
        SELECT * FROM zones
        WHERE status != 'rejected'
        AND ST_Contains(
            boundary,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
        )
        """, nativeQuery = true)
    List<Zone> findZonesContainingPoint(@Param("lng") double lng, @Param("lat") double lat);


    @Query(value = """
    SELECT id, name, zone_type AS "zoneType",
           ST_Y(ST_Centroid(boundary)) AS lat,
           ST_X(ST_Centroid(boundary)) AS lng
    FROM zones
    """, nativeQuery = true)
    List<ZoneSummary> findAllZoneSummaries();

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO zones (name, zone_type, boundary, status, confidence, photo_url)
        VALUES (
        :name,
        'user_reported',
        ST_Buffer(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)::geometry,
        :status,
        :confidence,
        :photoUrl
    )
    """, nativeQuery = true)
    void insertUserReportedZoneWithVerification(@Param("name") String name, @Param("lat") double lat,
                             @Param("lng") double lng, @Param("radiusMeters") double radiusMeters,
                             @Param("status") String status, @Param("confidence") Double confidence,
                             @Param("photoUrl") String photoUrl);

    @Query(value = "SELECT id FROM zones ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Long findLastInsertedZoneId();

    @Query(value = """
        SELECT id, status FROM zones
        WHERE zone_type = 'user_reported'
        AND ST_DWithin(
            boundary::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            :thresholdMeters
        )
        ORDER BY id
        LIMIT 1
        """, nativeQuery = true)
    Optional<NearbyZoneMatch> findNearbyUserReportedZone(@Param("lng") double lng,
                                                          @Param("lat") double lat,
                                                          @Param("thresholdMeters") double thresholdMeters);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO zone_reports (zone_id, reporter_id)
        VALUES (:zoneId, :reporterId)
        ON CONFLICT (zone_id, reporter_id) DO NOTHING
        """, nativeQuery = true)
    int insertZoneReport(@Param("zoneId") Long zoneId, @Param("reporterId") String reporterId);

    @Query(value = "SELECT COUNT(*) FROM zone_reports WHERE zone_id = :zoneId", nativeQuery = true)
    long countReportsForZone(@Param("zoneId") Long zoneId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE zones SET status = :status WHERE id = :zoneId", nativeQuery = true)
    void updateZoneStatus(@Param("zoneId") Long zoneId, @Param("status") String status);

    @Modifying
    @Transactional
    @Query(value = "UPDATE zones SET status = :status, photo_url = :photoUrl WHERE id = :zoneId", nativeQuery = true)
    void updateZoneStatusAndPhoto(@Param("zoneId") Long zoneId, @Param("status") String status,
                                   @Param("photoUrl") String photoUrl);

    @Query(value = """
        SELECT z.id, z.name, z.photo_url AS "photoUrl", z.confidence,
               COALESCE(COUNT(zr.id), 0) AS "reportCount"
        FROM zones z
        LEFT JOIN zone_reports zr ON zr.zone_id = z.id
        WHERE z.status = 'pending'
        GROUP BY z.id, z.name, z.photo_url, z.confidence
        ORDER BY z.id DESC
        """, nativeQuery = true)
    List<PendingZone> findPendingZones();
}