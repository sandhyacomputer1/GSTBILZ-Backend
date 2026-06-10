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

	private Long totalUsers;
	private Long totalCustomers;
	private Long totalShops;
	private Double totalCollection;
}
