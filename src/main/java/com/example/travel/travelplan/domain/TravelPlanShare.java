package com.example.travel.travelplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "travel_plan_shares")
public class TravelPlanShare {
    @Id
    @Column(name = "travel_plan_id")
    private Long travelPlanId;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", insertable = false, updatable = false)
    private TravelPlan travelPlan;
    @Column(name = "token_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TravelPlanShare() {}
    public TravelPlanShare(Long travelPlanId, byte[] tokenHash, Instant createdAt, Instant expiresAt) {
        this.travelPlanId = travelPlanId;
        this.tokenHash = tokenHash.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    public Long travelPlanId() { return travelPlanId; }
    public byte[] tokenHash() { return tokenHash.clone(); }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
}
