package com.project.configmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SettingManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SettingManagerApplication.class, args);
	}

}