package com.idz.colman24class1.model.networking
import okhttp3.Interceptor
import okhttp3.Response

class AnalyzedResultInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}
