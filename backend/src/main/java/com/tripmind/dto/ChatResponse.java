package com.tripmind.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
    private String destination;
    private String description;
    private String imageUrl;
    private String weather;
    private Map<String, Object> weatherData;
    private String crowd;
    private String crowdReason;
    private String budget;
    private Map<String, Object> budgetEstimate;
    private List<String> activities;
    private String justification;
    private Map<String, Object> option1;
    private Map<String, Object> option2;
    private Map<String, Object> costBreakdown;
    private Boolean restrictive;
    private Integer minimumRequiredBudgetIncrease;
    private Integer minimumRequiredDaysIncrease;
    
    // Smart Fallback Additions
    private String status;
    private String reason;
    private String explanation;
    private Map<String, Object> required;
    private List<Map<String, Object>> alternatives;

    // Getters & Setters
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public Map<String, Object> getWeatherData() { return weatherData; }
    public void setWeatherData(Map<String, Object> weatherData) { this.weatherData = weatherData; }

    public String getCrowd() { return crowd; }
    public void setCrowd(String crowd) { this.crowd = crowd; }

    public String getCrowdReason() { return crowdReason; }
    public void setCrowdReason(String crowdReason) { this.crowdReason = crowdReason; }

    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }

    public Map<String, Object> getBudgetEstimate() { return budgetEstimate; }
    public void setBudgetEstimate(Map<String, Object> budgetEstimate) { this.budgetEstimate = budgetEstimate; }

    public List<String> getActivities() { return activities; }
    public void setActivities(List<String> activities) { this.activities = activities; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public Map<String, Object> getOption1() { return option1; }
    public void setOption1(Map<String, Object> option1) { this.option1 = option1; }

    public Map<String, Object> getOption2() { return option2; }
    public void setOption2(Map<String, Object> option2) { this.option2 = option2; }

    public Map<String, Object> getCostBreakdown() { return costBreakdown; }
    public void setCostBreakdown(Map<String, Object> costBreakdown) { this.costBreakdown = costBreakdown; }

    public Boolean getRestrictive() { return restrictive; }
    public void setRestrictive(Boolean restrictive) { this.restrictive = restrictive; }

    public Integer getMinimumRequiredBudgetIncrease() { return minimumRequiredBudgetIncrease; }
    public void setMinimumRequiredBudgetIncrease(Integer minimumRequiredBudgetIncrease) { this.minimumRequiredBudgetIncrease = minimumRequiredBudgetIncrease; }

    public Integer getMinimumRequiredDaysIncrease() { return minimumRequiredDaysIncrease; }
    public void setMinimumRequiredDaysIncrease(Integer minimumRequiredDaysIncrease) { this.minimumRequiredDaysIncrease = minimumRequiredDaysIncrease; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Map<String, Object> getRequired() { return required; }
    public void setRequired(Map<String, Object> required) { this.required = required; }

    public List<Map<String, Object>> getAlternatives() { return alternatives; }
    public void setAlternatives(List<Map<String, Object>> alternatives) { this.alternatives = alternatives; }
}
