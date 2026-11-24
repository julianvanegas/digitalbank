package com.udea.digitalbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DigitalbankApplication {

	public static void main(String[] args) {
		SpringApplication.run(DigitalbankApplication.class, args);
	}

}
