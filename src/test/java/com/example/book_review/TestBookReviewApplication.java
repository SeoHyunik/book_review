package com.example.book_review;

import org.springframework.boot.SpringApplication;

public class TestBookReviewApplication {

	public static void main(String[] args) {
		SpringApplication.from(BookReviewApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
