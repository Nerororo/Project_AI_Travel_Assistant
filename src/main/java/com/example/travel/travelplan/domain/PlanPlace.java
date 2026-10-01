package com.example.travel.travelplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "plan_places")
public class PlanPlace {
    public enum Role { ATTRACTION, HOTEL, RESTAURANT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "travel_plan_id", nullable = false)
    private Long travelPlanId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", insertable = false, updatable = false)
    private TravelPlan travelPlan;
    @Column(name = "kakao_place_id", nullable = false, length = 255)
    private String kakaoPlaceId;
    @Column(name = "place_url", nullable = false, length = 1000)
    private String placeUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;
    @Column(length = 1000)
    private String memo;
    @Column(name = "stay_minutes")
    private Integer stayMinutes;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanPlace() {}
    public PlanPlace(Long travelPlanId, String kakaoPlaceId, String placeUrl, Role role,
                     String displayName, String memo, Integer stayMinutes) {
        this.travelPlanId = travelPlanId;
        this.kakaoPlaceId = kakaoPlaceId;
        this.placeUrl = placeUrl;
        this.role = role;
        this.displayName = displayName;
        this.memo = memo;
        this.stayMinutes = stayMinutes;
    }
    public Long id() { return id; }
    public Long travelPlanId() { return travelPlanId; }
    public String kakaoPlaceId() { return kakaoPlaceId; }
    public String placeUrl() { return placeUrl; }
    public Role role() { return role; }
    public String displayName() { return displayName; }
    public String memo() { return memo; }
    public Integer stayMinutes() { return stayMinutes; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public void edit(String displayName, String memo) {
        this.displayName = displayName;
        this.memo = memo;
    }
}
