package com.switchbase;

import org.springframework.boot.SpringApplication;

public class TestSwitchbaseApplication {

	public static void main(String[] args) {
		SpringApplication.from(SwitchbaseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
