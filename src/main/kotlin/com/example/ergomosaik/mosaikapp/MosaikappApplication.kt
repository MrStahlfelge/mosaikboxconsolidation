package com.example.ergomosaik.mosaikapp

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootApplication
class MosaikappApplication {
	private val okHttpClient = OkHttpClient()

	@Bean
	fun getOkHttpClient(): OkHttpClient {
		return okHttpClient
	}

	@Bean
	@Primary
	fun objectMapper(): ObjectMapper {
		// enables controller methods annotated with @ResponseBody to directly return
		// Mosaik Actions and elements that will get serialized by Spring automatically
		return org.ergoplatform.mosaik.jackson.MosaikSerializer.getMosaikMapper()
	}
}

fun main(args: Array<String>) {
	runApplication<MosaikappApplication>(*args)
}
