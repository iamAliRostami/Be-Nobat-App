package com.leon.be_nobat.helpers

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * SSL helper that ensures compatibility with development/staging tunnel providers (e.g. Serveo / Let's Encrypt ECDSA)
 * across older and newer Android devices without certificate chain validation errors.
 */
object SslHelper {

    private val permissiveTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val tunnelSslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(permissiveTrustManager), SecureRandom())
        }
    }

    fun configureHttps(connection: HttpsURLConnection) {
        val host = connection.url?.host.orEmpty().lowercase()
        // For development/tunnel domains, production domain, or local IP, permit the certificate
        if (host.endsWith("serveousercontent.com") || host.endsWith("benobat.ir") || host == "localhost" || host == "10.0.2.2" || host == "127.0.0.1") {
            try {
                connection.sslSocketFactory = tunnelSslContext.socketFactory
                connection.hostnameVerifier = HostnameVerifier { hostname, session ->
                    hostname.endsWith("serveousercontent.com") ||
                            hostname.endsWith("benobat.ir") ||
                            HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                }
            } catch (_: Exception) {
            }
        }
    }
}
