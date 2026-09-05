package com.example;

import com.example.repository.CustomerRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RouterFunctions.route;
import static org.springframework.web.servlet.function.ServerResponse.ok;
import java.util.Map;

@SpringBootApplication
public class ServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> myRoutes(CustomerRepository repository) {
		return route()//
			.GET("/hello", _ -> ok().body(Map.of("message", "Hello World")))//
			.GET("/customers", _ -> ok().body(repository.findAll()))//
			.build();
	}

}
