package com.example.demo;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public Function<String, String> translate() {
		return input -> {
			final String fromLang = "en";
			final String toLang = "es";
			final String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + fromLang + "&tl="
					+ toLang + "&dt=t&q=" + input;

			final RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

			String result = response.getBody();

			// clean up results
			int index = result.indexOf(",");
			result = result.substring(3, index).replace("\"", "");
			return result;
		};
	}

	@Bean
	public Function<UserReview, UserReview> translateReview() {
		return input -> {
			final UserReview output = input;
			output.setComment(translate().apply(input.getComment()));
			return output;
    	};
	}
	
}
