package br.com.susagenda.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;

@Configuration
public class SecurityConfig {
	@Bean
	UserDetailsService users(@Value("${app.operator-password}") String operator,
			@Value("${app.reader-password}") String reader) {
		var encoder = new BCryptPasswordEncoder();
		return new InMemoryUserDetailsManager(
				User.withUsername("operador").password("{bcrypt}" + encoder.encode(operator)).roles("OPERATOR").build(),
				User.withUsername("leitor").password("{bcrypt}" + encoder.encode(reader)).roles("READER").build());
	}

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		// API sem sessão/cookies de aplicação, destinada a clientes explícitos
		// Postman/Swagger.
		return http.csrf(csrf -> csrf.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/swagger-ui.html",
								"/swagger-ui/**", "/v3/api-docs/**", "/error")
						.permitAll().requestMatchers("/actuator/**").hasRole("OPERATOR")
						.requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("OPERATOR", "READER")
						.requestMatchers("/api/**").hasRole("OPERATOR").anyRequest().denyAll())
				.httpBasic(Customizer.withDefaults()).build();
	}

	@Bean
	OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("SUS Agenda").version("1.0.0").description(
				"MVP acadêmico. Operações realizadas por atendentes; dados fictícios. Fila FIFO por unidade e especialidade."))
				.components(new Components().addSecuritySchemes("basicAuth",
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
				.addSecurityItem(new SecurityRequirement().addList("basicAuth"));
	}
}
