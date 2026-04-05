package de.keiss.selfhealing.app.data

import retrofit2.http.GET
import retrofit2.http.Query

interface ConnectionApi {
    @GET("/api/v1/connections")
    suspend fun searchConnections(
        @Query("from") from: String,
        @Query("to") to: String
    ): List<Connection>
}
