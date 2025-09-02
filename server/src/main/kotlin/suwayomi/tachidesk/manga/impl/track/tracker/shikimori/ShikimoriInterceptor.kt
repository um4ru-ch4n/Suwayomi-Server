package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import eu.kanade.tachiyomi.AppInfo
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriOAuth
import java.io.IOException

class ShikimoriInterceptor(
    private val shikimori: Shikimori,
) : Interceptor {
    private var oauth: ShikimoriOAuth? = shikimori.loadOAuth()
    private val tokenExpired get() = shikimori.getIfAuthExpired()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) {
            throw TokenExpired()
        }

        val originalRequest = chain.request()

        // Refresh access token if expired
        if (oauth?.isExpired() == true) {
            try {
                runBlocking { refreshToken(chain) }
            } catch (e: Exception) {
                shikimori.setAuthExpired()
                throw TokenExpired()
            }
        }

        // Throw on null auth
        if (oauth == null) {
            throw IOException("Shikimori: User is not authenticated")
        }

        // Add the authorization header to the original request
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
                .header("User-Agent", "Suwayomi v${AppInfo.getVersionName()}")
                .build()

        val response = chain.proceed(authRequest)

        // Handle 401 Unauthorized response (invalid token)
        if (response.code == 401) {
            response.close()
            try {
                runBlocking { refreshToken(chain) }
                // Retry the original request with new token
                val retryRequest = originalRequest
                    .newBuilder()
                    .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
                    .header("User-Agent", "Suwayomi v${AppInfo.getVersionName()}")
                    .build()
                return chain.proceed(retryRequest)
            } catch (e: Exception) {
                shikimori.setAuthExpired()
                throw TokenExpired()
            }
        }

        return response
    }

    /**
     * Called when the user authenticates with Shikimori for the first time. Sets the oauth object.
     */
    fun setAuth(oauth: ShikimoriOAuth?) {
        this.oauth = oauth
        shikimori.saveOAuth(oauth)
    }

    /**
     * Refresh the access token using the refresh token
     */
    private suspend fun refreshToken(chain: Interceptor.Chain) {
        val currentOAuth = oauth ?: throw IOException("No OAuth token available for refresh")
        val newOAuth = shikimori.refreshAccessToken(currentOAuth.refreshToken)
        this.oauth = newOAuth
        shikimori.saveOAuth(newOAuth)
    }
}
