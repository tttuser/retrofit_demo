package com.example.retrofitdemo.network

import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * ApiResultCallAdapterFactory: A Retrofit CallAdapter.Factory that wraps API responses in ApiResult.
 * 
 * Purpose:
 * - Automatically wraps success/error responses in ApiResult sealed class
 * - Eliminates boilerplate error handling code
 * - Makes error handling type-safe and explicit
 * 
 * How it works:
 * 1. Retrofit asks this factory if it can handle a return type (e.g., Call<ApiResult<User>>)
 * 2. If the return type is Call<ApiResult<T>>, we return an ApiResultCallAdapter
 * 3. The adapter wraps the original Call to transform responses into ApiResult
 * 
 * Source reading notes:
 * - CallAdapter.Factory is part of Retrofit's extensibility mechanism
 * - get() is called for each API method to determine if this factory handles the return type
 * - The adapter's adapt() method wraps the original Call with custom behavior
 */
class ApiResultCallAdapterFactory : CallAdapter.Factory() {
    
    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        // Check if the return type is a Call
        if (getRawType(returnType) != Call::class.java) {
            return null
        }
        
        // Get the response type inside Call<...>
        val callType = getParameterUpperBound(0, returnType as ParameterizedType)
        
        // Check if the response type is ApiResult
        if (getRawType(callType) != ApiResult::class.java) {
            return null
        }
        
        // Get the actual data type inside ApiResult<...>
        val dataType = getParameterUpperBound(0, callType as ParameterizedType)
        
        return ApiResultCallAdapter<Any>(dataType)
    }
    
    /**
     * The actual CallAdapter that wraps Call<T> into Call<ApiResult<T>>.
     */
    private class ApiResultCallAdapter<T>(
        private val dataType: Type
    ) : CallAdapter<T, Call<ApiResult<T>>> {
        
        override fun responseType(): Type = dataType
        
        override fun adapt(call: Call<T>): Call<ApiResult<T>> {
            return ApiResultCall(call)
        }
    }
    
    /**
     * Wrapper Call that transforms responses into ApiResult.
     * 
     * Implementation notes:
     * - Delegates most methods to the original call
     * - Overrides execute() and enqueue() to wrap responses in ApiResult
     * - Catches all exceptions and wraps them in ApiResult.Error
     */
    private class ApiResultCall<T>(
        private val delegate: Call<T>
    ) : Call<ApiResult<T>> {
        
        override fun execute(): retrofit2.Response<ApiResult<T>> {
            return try {
                val response = delegate.execute()
                val apiResult = if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        ApiResult.Success(body)
                    } else {
                        ApiResult.Error(
                            exception = NullPointerException("Response body is null"),
                            errorBody = null
                        )
                    }
                } else {
                    ApiResult.Error(
                        exception = HttpException(response.code(), response.message()),
                        errorBody = response.errorBody()?.string()
                    )
                }
                retrofit2.Response.success(apiResult)
            } catch (e: Exception) {
                retrofit2.Response.success(
                    ApiResult.Error(
                        exception = e,
                        errorBody = null
                    )
                )
            }
        }
        
        override fun enqueue(callback: retrofit2.Callback<ApiResult<T>>) {
            delegate.enqueue(object : retrofit2.Callback<T> {
                override fun onResponse(call: Call<T>, response: retrofit2.Response<T>) {
                    val apiResult = if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            ApiResult.Success(body)
                        } else {
                            ApiResult.Error(
                                exception = NullPointerException("Response body is null"),
                                errorBody = null
                            )
                        }
                    } else {
                        ApiResult.Error(
                            exception = HttpException(response.code(), response.message()),
                            errorBody = response.errorBody()?.string()
                        )
                    }
                    callback.onResponse(
                        this@ApiResultCall,
                        retrofit2.Response.success(apiResult)
                    )
                }
                
                override fun onFailure(call: Call<T>, t: Throwable) {
                    val apiResult = ApiResult.Error(
                        exception = t,
                        errorBody = null
                    )
                    callback.onResponse(
                        this@ApiResultCall,
                        retrofit2.Response.success(apiResult)
                    )
                }
            })
        }
        
        override fun isExecuted(): Boolean = delegate.isExecuted
        
        override fun cancel() = delegate.cancel()
        
        override fun isCanceled(): Boolean = delegate.isCanceled
        
        override fun clone(): Call<ApiResult<T>> = ApiResultCall(delegate.clone())
        
        override fun request(): okhttp3.Request = delegate.request()
        
        override fun timeout(): okio.Timeout = delegate.timeout()
    }
    
    /**
     * Simple HttpException to represent HTTP error responses.
     */
    class HttpException(
        val code: Int,
        override val message: String
    ) : Exception("HTTP $code: $message")
}
