package com.example.nureform.data.api

import com.example.nureform.data.model.SchedulingResponse
import retrofit2.Response
import retrofit2.http.GET

interface SchedulingApiService {
    @GET("shifts/current-week")
    suspend fun runScheduling(): Response<SchedulingResponse>
}
