package com.tripmind.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class BudgetService {

    // Hotel tiers (per room per night) — ordered from cheapest to most expensive
    private static final int[] HOTEL_TIERS = {800, 1200, 2500, 5000, 9000};
    private static final String[] TIER_NAMES = {"Hostel", "Budget", "Standard", "Premium", "Luxury"};
    private static final int[] FOOD_PER_PERSON = {250, 400, 600, 1000, 1500};

    // Peak months
    private static final Set<Month> PEAK_MONTHS = Set.of(
            Month.MAY, Month.JUNE, Month.DECEMBER
    );

    public Map<String, Object> calculateBudget(String destination, int days, int totalBudget, int travelers, int estTravelCost) {
        return calculateBudget(destination, days, totalBudget, travelers, estTravelCost, null);
    }

    public Map<String, Object> calculateBudget(String destination, int days, int totalBudget,
                                                int travelers, int estTravelCost, String travelDateStr) {
        Map<String, Object> budget = new HashMap<>();

        // ===== Seasonal Pricing Multiplier =====
        double seasonMultiplier = 1.0;
        String seasonNote = "Regular season pricing";

        if (travelDateStr != null && !travelDateStr.isEmpty()) {
            try {
                LocalDate travelDate = LocalDate.parse(travelDateStr);
                Month month = travelDate.getMonth();
                DayOfWeek dow = travelDate.getDayOfWeek();

                if (PEAK_MONTHS.contains(month)) {
                    seasonMultiplier = 1.30; // 30% peak increase
                    seasonNote = "Peak season — prices are ~30% higher";
                } else if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    seasonMultiplier = 1.10; // 10% weekend bump
                    seasonNote = "Weekend travel — slight price increase";
                } else {
                    seasonMultiplier = 0.85; // 15% off-season discount
                    seasonNote = "Off-season — you're saving ~15%!";
                }
            } catch (Exception ignored) {}
        }

        int roomsNeeded = (int) Math.ceil((double) travelers / 2);
        int nights = Math.max(days - 1, 1);

        // ===== STRICT BUDGET CONTROL: try from highest tier downward =====
        int chosenTierIndex = -1;
        int totalHotelCost = 0;
        int totalFoodCost = 0;
        int grandTotal = 0;

        // First, determine what tier the budget can support (start high, go low)
        for (int tier = HOTEL_TIERS.length - 1; tier >= 0; tier--) {
            int hotelCost = (int) (HOTEL_TIERS[tier] * seasonMultiplier) * roomsNeeded * nights;
            int foodCost = (int) (FOOD_PER_PERSON[tier] * seasonMultiplier) * travelers * days;
            int total = estTravelCost + hotelCost + foodCost;

            if (total <= totalBudget) {
                chosenTierIndex = tier;
                totalHotelCost = hotelCost;
                totalFoodCost = foodCost;
                grandTotal = total;
                break;
            }
        }

        // If even the cheapest tier exceeds budget, use cheapest and flag it
        if (chosenTierIndex == -1) {
            chosenTierIndex = 0;
            totalHotelCost = (int) (HOTEL_TIERS[0] * seasonMultiplier) * roomsNeeded * nights;
            totalFoodCost = (int) (FOOD_PER_PERSON[0] * seasonMultiplier) * travelers * days;
            grandTotal = estTravelCost + totalHotelCost + totalFoodCost;

            // If still over budget, reduce travel cost assumption
            if (grandTotal > totalBudget && estTravelCost > 0) {
                // Suggest bus/cheapest transport
                estTravelCost = (int) (estTravelCost * 0.5);
                grandTotal = estTravelCost + totalHotelCost + totalFoodCost;
            }

            budget.put("budgetWarning", "Your budget is tight. We've optimized to the most affordable options.");
        }

        budget.put("travel", estTravelCost);
        budget.put("stay", totalHotelCost);
        budget.put("food", totalFoodCost);
        budget.put("total", grandTotal);
        budget.put("tier", TIER_NAMES[chosenTierIndex]);
        budget.put("seasonNote", seasonNote);
        budget.put("seasonMultiplier", seasonMultiplier);

        // ===== Option 2: Upgraded (one tier higher, may exceed budget) =====
        if (chosenTierIndex < HOTEL_TIERS.length - 1) {
            int upgradeTier = chosenTierIndex + 1;
            int upgradeHotel = (int) (HOTEL_TIERS[upgradeTier] * seasonMultiplier) * roomsNeeded * nights;
            int upgradeFood = (int) (FOOD_PER_PERSON[upgradeTier] * seasonMultiplier) * travelers * days;
            int upgradeTotal = estTravelCost + upgradeHotel + upgradeFood;
            budget.put("upgradedTotal", upgradeTotal);
            budget.put("upgradedTier", TIER_NAMES[upgradeTier]);
        }

        return budget;
    }
}
