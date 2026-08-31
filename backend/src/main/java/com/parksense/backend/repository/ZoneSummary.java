package com.parksense.backend.repository;

public interface ZoneSummary {
    Long getId();
    String getName();
    String getZoneType();
    Double getLat();
    Double getLng();
}