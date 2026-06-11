package com.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.filter.JwtRequestFilter;
import com.service.impl.AppUserDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final AppUserDetailsService appUserDetailsService;
	private final JwtRequestFilter jwtRequestFilter;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		http
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/login", "/encode", "/error", "/verify-email", "/register-shop",
								"/login/google", "/forgot-password", "/reset-password")
						.permitAll()
						.requestMatchers("/admin/shop-owners", "/admin/shop-owners/**").hasRole("SUPERADMIN")
						.requestMatchers(HttpMethod.DELETE, "/orders/**").hasAnyRole("SHOPOWNER", "SUPERADMIN")
						.requestMatchers("/categories", "/categories/**", "/items", "/items/**", "/admin/items",
								"/admin/items/**", "/admin/categories", "/admin/categories/**", "/orders", "/payments",
								"/dashboard", "/admin/profile")
						.hasAnyRole("EMPLOYEE", "SHOPOWNER", "SUPERADMIN")
						.requestMatchers("/admin/users", "/admin/user/**").hasAnyRole("SUPERADMIN", "SHOPOWNER")
						.requestMatchers("/admin/register").hasAnyRole("SUPERADMIN", "SHOPOWNER")
						.requestMatchers("/admin/**").hasRole("SUPERADMIN")
						.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring().requestMatchers("/invoices/**");
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {

		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of("http://localhost:5173", "https://billingsoftwergst.up.railway.app",
				"https://billingsoftwergst.up.railway.app"));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		return source;
	}

	@Bean
	public AuthenticationManager authenticationManager() {

		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(appUserDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());

		return new ProviderManager(authProvider);
	}
}