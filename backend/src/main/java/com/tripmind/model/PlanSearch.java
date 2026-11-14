package com.tripmind.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One planner search: what was asked and the top answer, for history and admin analytics. */
@Entity
@Table(name = "plan_searches", indexes = {
        @Index(name = "idx_plan_user", columnList = "userId"),
        @Index(name = "idx_plan_created", columnList = "createdAt")
})
public class PlanSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String query;

    private String status;
    private String topDestination;
    private String topDestinationId;
    private Integer total;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTopDestination() { return topDestination; }
    public void setTopDestination(String topDestination) { this.topDestination = topDestination; }
    public String getTopDestinationId() { return topDestinationId; }
    public void setTopDestinationId(String topDestinationId) { this.topDestinationId = topDestinationId; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** Alias so the admin UI's "destination" column works. */
    public String getDestination() { return topDestination; }
}
