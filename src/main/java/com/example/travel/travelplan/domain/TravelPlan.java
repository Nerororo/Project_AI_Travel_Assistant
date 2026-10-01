package com.example.travel.travelplan.domain;

import com.example.travel.route.algorithm.TravelMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "travel_plans")
public class TravelPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(name = "region_id", nullable = false, length = 100)
    private String regionId;
    @Column(name = "region_display_name", nullable = false, length = 100)
    private String regionDisplayName;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false, length = 20)
    private TravelMode travelMode;
    @Column(name = "meal_travel_buffer_minutes", nullable = false)
    private int mealTravelBufferMinutes;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TravelPlan() {}

    public TravelPlan(Long userId, String title, String regionId, String regionDisplayName,
                      LocalDate startDate, LocalDate endDate, TravelMode travelMode, int mealTravelBufferMinutes) {
        this.userId = userId;
        this.title = title;
        this.regionId = regionId;
        this.regionDisplayName = regionDisplayName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelMode = travelMode;
        this.mealTravelBufferMinutes = mealTravelBufferMinutes;
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public String title() { return title; }
    public String regionId() { return regionId; }
    public String regionDisplayName() { return regionDisplayName; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public TravelMode travelMode() { return travelMode; }
    public int mealTravelBufferMinutes() { return mealTravelBufferMinutes; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public void rename(String title) { this.title = title; }
}
