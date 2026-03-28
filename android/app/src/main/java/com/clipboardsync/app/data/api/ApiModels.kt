package com.clipboardsync.app.data.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String)

/** 网页二维码 JSON：仅含版本、API 根地址、一次性兑换码 */
@Serializable
data class QrLoginPayload(val v: Int, val u: String, val c: String)

@Serializable
data class QrRedeemRequest(val code: String)

@Serializable
data class QrRedeemResponse(val token: String, val username: String)

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
