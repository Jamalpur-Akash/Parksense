package com.parksense.backend.repository;

public interface PendingZone {
    Long getId();
    String getName();
    String getPhotoUrl();
    Double getConfidence();
    Long getReportCount();
}