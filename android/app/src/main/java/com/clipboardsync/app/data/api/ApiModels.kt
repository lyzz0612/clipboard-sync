package com.clipboardsync.app.data.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String)

@Serializable
data class RegisterResponse(val ok: Boolean, val message: String)

@Serializable
data class ClipItem(val id: String, val text: String, val createdAt: String)

@Serializable
data class ClipsResponse(val clips: List<ClipItem>)

@Serializable
data class DeltaResponse(val clips: List<ClipItem>, val serverTime: String)

@Serializable
data class PostClipRequest(val text: String)

@Serializable
data class PostClipResponse(val clip: ClipItem)

@Serializable
data class DeleteResponse(val ok: Boolean)

@Serializable
data class ErrorResponse(val error: String)
