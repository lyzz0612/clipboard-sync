package com.clipboardsync.app.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ClipboardApi {
    @POST("api/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/qr-redeem")
    suspend fun qrRedeem(@Body body: QrRedeemRequest): QrRedeemResponse

    @GET("api/clips")
    suspend fun getClips(): ClipsResponse

    @GET("api/clips/delta")
    suspend fun getClipsDelta(@Query("since") since: String): DeltaResponse

    @POST("api/clips")
    suspend fun postClip(@Body body: PostClipRequest): PostClipResponse

    @DELETE("api/clips/{id}")
    suspend fun deleteClip(@Path("id") id: String): DeleteResponse
}
