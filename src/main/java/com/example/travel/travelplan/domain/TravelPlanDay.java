package com.example.travel.travelplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "travel_plan_days")
public class TravelPlanDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "travel_plan_id", nullable = false)
    private Long travelPlanId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", insertable = false, updatable = false)
    private TravelPlan travelPlan;
    @Column(name = "day_number", nullable = false)
    private int dayNumber;
    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;
    @Column(name = "activity_start_time", nullable = false)
    private LocalTime activityStartTime;
    @Column(name = "activity_end_time", nullable = false)
    private LocalTime activityEndTime;

    protected TravelPlanDay() {}
    public TravelPlanDay(Long travelPlanId, int dayNumber, LocalDate travelDate,
                         LocalTime activityStartTime, LocalTime activityEndTime) {
        this.travelPlanId = travelPlanId;
        this.dayNumber = dayNumber;
        this.travelDate = travelDate;
        this.activityStartTime = activityStartTime;
        this.activityEndTime = activityEndTime;
    }
    public Long id() { return id; }
    public Long travelPlanId() { return travelPlanId; }
    public int dayNumber() { return dayNumber; }
    public LocalDate travelDate() { return travelDate; }
    public LocalTime activityStartTime() { return activityStartTime; }
    public LocalTime activityEndTime() { return activityEndTime; }
}
