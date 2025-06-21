package ru.romanow.todolist.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.OPTIONS
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver.*
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import ru.romanow.todolist.config.properties.ActuatorSecurityProperties
import ru.romanow.todolist.config.properties.OAuthLoginProperties

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(value = [OAuthLoginProperties::class, ActuatorSecurityProperties::class])
class SecurityConfiguration(
    private val actuatorSecurityProperties: ActuatorSecurityProperties,
) {

    @Bean
    @Order(FIRST)
    @ConditionalOnProperty("oauth2.security.enabled", havingValue = "true", matchIfMissing = true)
    fun tokenSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")
            .oauth2Login {
                it.defaultSuccessUrl("/callback", true)
            }
            .build()
    }

    @Bean
    @Order(SECOND)
    fun managementSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/manage/**", "/api-docs")
            .authorizeHttpRequests {
                it.requestMatchers("/manage/health/**", "/manage/prometheus", "/api-docs")
                    .permitAll()
                    .anyRequest().hasRole(actuatorSecurityProperties.role)
            }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(STATELESS) }
            .httpBasic {}
            .build()
    }

    @Bean
    @Order(THIRD)
    @ConditionalOnProperty("oauth2.security.enabled", havingValue = "false")
    fun disabledSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests {
                it.anyRequest().permitAll()
            }
            .csrf { it.disable() }
            .build()
    }

    @Bean
    @Order(THIRD)
    @ConditionalOnProperty("oauth2.security.enabled", havingValue = "true", matchIfMissing = true)
    fun protectedResourceSecurityFilterChain(
        http: HttpSecurity, properties: OAuth2ClientProperties
    ): SecurityFilterChain {
        val sources = mutableListOf<String>()
        for (p in PROVIDERS) {
            if (properties.provider.containsKey(p)) {
                sources.add(properties.provider[p]!!.issuerUri)
            }
        }
        val authenticationManagerResolver = fromTrustedIssuers(sources)
        return http
            .authorizeHttpRequests {
                it.requestMatchers(OPTIONS).permitAll()
                it.requestMatchers(GET, "/").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.authenticationManagerResolver(authenticationManagerResolver)
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(UNAUTHORIZED))
            }
            .csrf { it.disable() }
            .cors { }
            .build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun users(passwordEncoder: PasswordEncoder): UserDetailsService {
        val user = User.builder()
            .username(actuatorSecurityProperties.user)
            .password(passwordEncoder.encode(actuatorSecurityProperties.password))
            .roles(actuatorSecurityProperties.role)
            .build()
        return InMemoryUserDetailsManager(user)
    }

    companion object {
        private const val FIRST = 1
        private const val SECOND = 2
        private const val THIRD = 3
        private val PROVIDERS = listOf("auth0", "keycloak")
    }
}
