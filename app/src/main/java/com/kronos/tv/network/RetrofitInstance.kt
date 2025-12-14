package com.kronos.tv.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val BASE_URL = "https://api.themoviedb.org/3/"
    
    // ⚠️ PEGA TU API KEY COMPLETA AQUÍ
    private const val API_KEY = "688d65c7c7e7995438db052275db288d" 

    private val retrofit by lazy { 
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build() 
    }
    
    val api: TmdbService by lazy { retrofit.create(TmdbService::class.java) }
    
    fun getApiKey(): String = API_KEY
}
