package id.ndifix.dwichat

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val token: String, val user: UserDto)
data class UserDto(val id: String, val username: String)
data class UserSearchResponse(val users: List<UserDto>)
data class ConversationDto(val id: String, val otherUser: UserDto, val lastMessage: String?, val updatedAt: String?)
data class ConversationsResponse(val conversations: List<ConversationDto>)
data class MessageDto(val id: String, val conversationId: String, val senderId: String, val senderUsername: String?, val body: String, val createdAt: String)
data class MessagesResponse(val messages: List<MessageDto>)
data class CreateConversationRequest(val userId: String)

interface DwiChatApi {
    @POST("auth/register") suspend fun register(@Body body: AuthRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body body: AuthRequest): AuthResponse
    @GET("users/search") suspend fun searchUsers(@Header("Authorization") token: String, @Query("q") query: String): UserSearchResponse
    @GET("conversations") suspend fun conversations(@Header("Authorization") token: String): ConversationsResponse
    @POST("conversations") suspend fun createConversation(@Header("Authorization") token: String, @Body body: CreateConversationRequest): ConversationDto
    @GET("conversations/{id}/messages") suspend fun messages(@Header("Authorization") token: String, @Path("id") id: String): MessagesResponse
}

object ApiClient {
    val api: DwiChatApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(DwiChatApi::class.java)
}
