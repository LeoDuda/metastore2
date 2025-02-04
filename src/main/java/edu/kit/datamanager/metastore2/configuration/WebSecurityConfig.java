/*
 * Copyright 2018 Karlsruhe Institute of Technology.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.kit.datamanager.metastore2.configuration;

import edu.kit.datamanager.security.filter.KeycloakJwtProperties;
import edu.kit.datamanager.security.filter.KeycloakTokenFilter;
import edu.kit.datamanager.security.filter.KeycloakTokenValidator;
import edu.kit.datamanager.security.filter.NoAuthenticationFilter;
import javax.servlet.Filter;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 *
 * @author jejkal
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  private Logger logger;

  @Autowired
  private ApplicationProperties applicationProperties;

  @Autowired
  private KeycloakJwtProperties properties;

  @Value("${metastore.security.enable-csrf:true}")
  private boolean enableCsrf;
  @Value("${metastore.security.allowedOriginPattern:http[*]://localhost:[*]}")
  private String allowedOriginPattern;

  public WebSecurityConfig() {
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    HttpSecurity httpSecurity = http.authorizeRequests()
            .antMatchers(HttpMethod.OPTIONS, "/**").
            permitAll().
            and().
            sessionManagement().
            sessionCreationPolicy(SessionCreationPolicy.STATELESS).and();
    if (!enableCsrf) {
      httpSecurity = httpSecurity.csrf().disable();
    }
    httpSecurity = httpSecurity.addFilterAfter(keycloaktokenFilterBean(), BasicAuthenticationFilter.class);

    if (!applicationProperties.isAuthEnabled()) {
      logger.info("Authentication is DISABLED. Adding 'NoAuthenticationFilter' to authentication chain.");
      httpSecurity = httpSecurity.addFilterAfter(new NoAuthenticationFilter(applicationProperties.getJwtSecret(), authenticationManager()), KeycloakTokenFilter.class);
    } else {
      logger.info("Authentication is ENABLED.");
    }

    httpSecurity.
            authorizeRequests().
            antMatchers("/api/v1").authenticated();

    http.headers().cacheControl().disable();
  }

  @Bean
  public KeycloakTokenFilter keycloaktokenFilterBean() throws Exception {
    return new KeycloakTokenFilter(KeycloakTokenValidator.builder()
            .readTimeout(properties.getReadTimeoutms())
            .connectTimeout(properties.getConnectTimeoutms())
            .sizeLimit(properties.getSizeLimit())
            .jwtLocalSecret(applicationProperties.getJwtSecret())
            .build(properties.getJwkUrl(), properties.getResource(), properties.getJwtClaim()));
  }

  @Bean
  public KeycloakJwtProperties properties() {
    return new KeycloakJwtProperties();
  }

  @Bean
  public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
    DefaultHttpFirewall firewall = new DefaultHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);
    return firewall;
  }

  @Override
  public void configure(WebSecurity web) throws Exception {
    web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
  }

//  @Bean
//  CorsConfigurationSource corsConfigurationSource(){
//    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//    CorsConfiguration config = new CorsConfiguration();
//    config.addAllowedOrigin("http://localhost:3000");
//
//    source.registerCorsConfiguration("/**", config);
//    return source;
//  }
  @Bean
  public FilterRegistrationBean corsFilter() {
    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.addAllowedOriginPattern(allowedOriginPattern); // @Value: http://localhost:8080
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.addExposedHeader("Content-Range");
    config.addExposedHeader("ETag");

    source.registerCorsConfiguration("/**", config);
    FilterRegistrationBean<Filter> bean;
    bean = new FilterRegistrationBean<>(new CorsFilter(source));
    bean.setOrder(0);
    return bean;
  }

//  @Bean
//  CorsConfigurationSource corsConfigurationSource(){
//    CorsConfiguration configuration = new CorsConfiguration();
//    configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
//    configuration.setAllowedMethods(Arrays.asList("GET", "POST, PATCH, DELETE, OPTIONS, HEAD"));
//    configuration.setAllowedHeaders(Arrays.asList("content-range", "authorization", "location"));
//    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//    source.registerCorsConfiguration("/**", configuration);
//    return source;
//  }
//  @Bean
//  CorsFilter corsFilter(){
//    CorsFilter filter = new CorsFilter();
//    return filter;
//  }
//  @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurerAdapter() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/greeting-javaconfig").allowedOrigins("http://localhost:9000");
//            }
//        };
//    }
//  @Bean
//  public UserRepositoryImpl userRepositoryImpl(){
//    return new UserRepositoryImpl();
//  }
}
