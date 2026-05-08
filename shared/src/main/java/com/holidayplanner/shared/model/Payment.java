package com.holidayplanner.shared.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Reference to the booking (managed by BookingService)
    @Column(nullable = false)
    private UUID bookingId;

    // Reference to the organization (managed by OrganizationService)
    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime refundedAt;

    // Optional note from accountant
    private String note;

    // Denormalized for notifications — populated from BookingCreated event
    private String parentEmail;
    private String eventName;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
