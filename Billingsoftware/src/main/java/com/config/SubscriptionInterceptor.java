package com.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.repository.UserRepository;
import com.service.SubscriptionService;
import com.entity.UserEntity;
import com.io.SubscriptionStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SubscriptionInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionInterceptor(UserRepository userRepository, SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Check if the path belongs to restricted APIs
        boolean isOrderAction = path.startsWith("/orders");
        boolean isItemAction = path.startsWith("/items") && !method.equalsIgnoreCase("GET");
        boolean isCategoryAction = path.startsWith("/categories") && !method.equalsIgnoreCase("GET");
        boolean isReportAction = path.startsWith("/api/reports");
        boolean isInvoiceAction = path.startsWith("/api/invoices");
        boolean isWhatsAppAction = path.startsWith("/whatsapp");
        boolean isImportAction = path.startsWith("/admin/items/import") || path.startsWith("/api/items/import");

        if (isOrderAction || isItemAction || isCategoryAction || isReportAction || isInvoiceAction || isWhatsAppAction || isImportAction) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                UserEntity user = userRepository.findByEmail(email).orElse(null);
                if (user != null && !"ROLE_SUPERADMIN".equals(user.getRole())) {
                    String shopOwnerId = "ROLE_EMPLOYEE".equals(user.getRole()) ? user.getShopOwnerId() : user.getUserId();
                    if (shopOwnerId != null) {
                        SubscriptionStatusResponse subStatus = subscriptionService.getMySubscriptionStatus(shopOwnerId);
                        if (subStatus != null && subStatus.isExpired()) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\": \"Subscription expired. Please contact the Super Admin.\"}");
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
