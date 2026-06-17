package com.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import com.io.DashboardResponse;
import com.io.OrderResponse;
import com.io.SubscriptionStatsResponse;
import com.service.NotificationService;
import com.service.OrderService;
import com.service.SubscriptionService;
import com.repository.UserRepository;
import com.repository.OrderEntityRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final OrderService orderService;
    private final UserRepository userRepository;
    private final OrderEntityRepository orderEntityRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;

    @GetMapping
    public DashboardResponse getDashboardData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_SUPERADMIN"));

        LocalDate today = LocalDate.now();

        Double todaySale = orderService.sumSalesByDate(today);
        Long todayOrderCount = orderService.countByOrderDate(today);
        List<OrderResponse> recentOrders = orderService.findRecentOrders();

        DashboardResponse response = new DashboardResponse();
        response.setTodaySales(todaySale != null ? todaySale : 0.0);
        response.setTodayOrderCount(todayOrderCount != null ? todayOrderCount : 0L);
        response.setRecentOrders(recentOrders);

        if (isSuperAdmin) {
            response.setTotalUsers(userRepository.count());
            response.setTotalShops(userRepository.countByRole("ROLE_SHOPOWNER"));
            response.setTotalCustomers(orderEntityRepository.countUniqueCustomers());
            Double totalColl = orderEntityRepository.sumAllSales();
            response.setTotalCollection(totalColl != null ? totalColl : 0.0);

            SubscriptionStatsResponse subStats = subscriptionService.getStats();
            response.setTrialAccounts(subStats.getTrialAccounts());
            response.setActiveSubscriptions(subStats.getActiveSubscriptions());
            response.setExpiredSubscriptions(subStats.getExpiredSubscriptions());
            response.setExpiringWithin7Days(subStats.getExpiringWithin7Days());
            response.setMonthlySubscriptionRevenue(subStats.getMonthlyRevenue());
            response.setYearlySubscriptionRevenue(subStats.getYearlyRevenue());

            // Unread notifications count for the super admin
            String adminEmail = authentication.getName();
            com.entity.UserEntity adminUser = userRepository.findByEmail(adminEmail).orElse(null);
            if (adminUser != null) {
                response.setUnreadNotifications(notificationService.getUnreadCount(adminUser.getUserId()));
            }
        }

        return response;
    }
}