package com.io;

import lombok.Data;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardResponse {
	private Double todaySales;
	private Long todayOrderCount;
	private List<OrderResponse> recentOrders;

	// SuperAdmin stats
	private Long totalUsers;
	private Long totalCustomers;
	private Long totalShops;
	private Double totalCollection;

	// Subscription stats (SuperAdmin only)
	private Long trialAccounts;
	private Long activeSubscriptions;
	private Long expiredSubscriptions;
	private Long expiringWithin7Days;
	private Double monthlySubscriptionRevenue;
	private Double yearlySubscriptionRevenue;
	private Long unreadNotifications;
}
