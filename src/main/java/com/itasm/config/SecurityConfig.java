package com.itasm.config;

import com.itasm.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Public resources
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()

                        // 2. Employee self-service endpoints (strictly ordered before broad module rules)
                        .requestMatchers("/api/employees/me").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/assets/me").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/software/me").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/asset-requests/me").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/asset-requests").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/tickets/me").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/api/tickets", "/api/tickets/*").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/tickets").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/notifications", "/api/notifications/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/ai/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/search").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/auth/me").hasAnyRole("ADMIN", "EMPLOYEE")

                        // 3. Admin-only administrative modules
                        .requestMatchers("/api/dashboard", "/api/dashboard/**").hasRole("ADMIN")
                        .requestMatchers("/api/reports", "/api/reports/**").hasRole("ADMIN")
                        .requestMatchers("/api/procurement", "/api/procurement/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit", "/api/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/security-events", "/api/security-events/**").hasRole("ADMIN")
                        .requestMatchers("/api/employees", "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers("/api/assets", "/api/assets/**").hasRole("ADMIN")
                        .requestMatchers("/api/software", "/api/software/**").hasRole("ADMIN")
                        .requestMatchers("/api/asset-requests", "/api/asset-requests/**").hasRole("ADMIN")
                        .requestMatchers("/api/tickets/*/status", "/api/tickets/*/assign").hasRole("ADMIN")

                        // 4. Fallback: all remaining API endpoints require authenticated role
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
