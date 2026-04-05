package de.keiss.selfhealing.app.data

import de.keiss.selfhealing.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ConnectionRepository {

    private val api: ConnectionApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ConnectionApi::class.java)
    }

    suspend fun search(from: String, to: String): Result<List<Connection>> {
        return try {
            val connections = api.searchConnections(from, to)
            Result.success(connections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
