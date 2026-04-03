package com.tripmind.dto;

public class ChatRequest {
    private Integer budget;
    private Integer duration;
    private String travelType;
    private String originCity;
    private Integer travelersCount;
    private String mood;
    private String weatherPref;
    private String crowdTolerance;
    private String stressLevel;
    private String psychProfile;
    private String travelStartDate;
    private String travelEndDate;
    private Long userId;

    /**
     * How many times the client has already relaxed budget/duration after a failed constraint check.
     * After {@code 2}, the server stops suggesting automatic budget escalations.
     */
    private Integer constraintEscalationAttempts;

    // Getters & Setters
    public Integer getBudget() { return budget; }
    public void setBudget(Integer budget) { this.budget = budget; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getTravelType() { return travelType; }
    public void setTravelType(String travelType) { this.travelType = travelType; }

    public String getOriginCity() { return originCity; }
    public void setOriginCity(String originCity) { this.originCity = originCity; }

    public Integer getTravelersCount() { return travelersCount; }
    public void setTravelersCount(Integer travelersCount) { this.travelersCount = travelersCount; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getWeatherPref() { return weatherPref; }
    public void setWeatherPref(String weatherPref) { this.weatherPref = weatherPref; }

    public String getCrowdTolerance() { return crowdTolerance; }
    public void setCrowdTolerance(String crowdTolerance) { this.crowdTolerance = crowdTolerance; }

    public String getStressLevel() { return stressLevel; }
    public void setStressLevel(String stressLevel) { this.stressLevel = stressLevel; }

    public String getPsychProfile() { return psychProfile; }
    public void setPsychProfile(String psychProfile) { this.psychProfile = psychProfile; }

    public String getTravelStartDate() { return travelStartDate; }
    public void setTravelStartDate(String travelStartDate) { this.travelStartDate = travelStartDate; }

    public String getTravelEndDate() { return travelEndDate; }
    public void setTravelEndDate(String travelEndDate) { this.travelEndDate = travelEndDate; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getConstraintEscalationAttempts() { return constraintEscalationAttempts; }
    public void setConstraintEscalationAttempts(Integer constraintEscalationAttempts) {
        this.constraintEscalationAttempts = constraintEscalationAttempts;
    }

    // Input validation
    public void validate() {
        if (budget != null && budget < 0) budget = 0;
        if (budget != null && budget > 1000000) budget = 1000000;
        if (duration != null && duration < 1) duration = 1;
        if (duration != null && duration > 15) duration = 15;
        if (constraintEscalationAttempts != null && constraintEscalationAttempts < 0) constraintEscalationAttempts = 0;
        if (constraintEscalationAttempts != null && constraintEscalationAttempts > 20) constraintEscalationAttempts = 20;
    }
}
