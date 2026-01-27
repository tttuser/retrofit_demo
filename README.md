
# Retrofit + Moshi Demo Project

A complete Kotlin Android demo project showcasing Retrofit HTTP client with Moshi JSON parsing, featuring source-reading oriented notes and comprehensive reproducible tests.

## Overview

This project demonstrates:
- **Retrofit 2.11.0**: HTTP client for Android and Java
- **OkHttp 4.12.0**: HTTP client used by Retrofit
- **Moshi 1.15.1**: Modern JSON library for Android and Java

## Features

### 1. API Service Interface (`ApiService.kt`)
Demonstrates various Retrofit features:
- **Path parameters** (`@Path`): Substitute values in URL paths
- **Query parameters** (`@Query`): Add query strings to URLs
- **Response<T>**: Access HTTP metadata (status codes, headers)
- **Call<T>**: Lazy call execution with cancellation support
- **Form-encoded requests** (`@FormUrlEncoded`, `@Field`): Send data as URL-encoded forms

### 2. OkHttp Configuration (`RetrofitClient.kt`)
Complete HTTP client setup with:
- **RequestIdInterceptor**: Adds unique `X-Request-Id` header to all requests for tracing
- **LoggingInterceptor**: Logs request/response details with simple redaction for sensitive data
- **Disk cache**: 10MB cache to reduce network calls
- **Timeouts**: 30-second connect/read/write timeouts

### 3. Custom Call Adapter (`ApiResultCallAdapterFactory.kt`)
- Wraps API responses in `ApiResult` sealed class
- Provides type-safe error handling
- Eliminates boilerplate error handling code
- Supports `Call<ApiResult<T>>` return types

### 4. ApiResult Sealed Class
```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Throwable, val errorBody: String?) : ApiResult<Nothing>()
}
```

Type-safe result wrapper that forces explicit error handling.

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/retrofitdemo/
│   │   │   ├── model/
│   │   │   │   ├── User.kt
│   │   │   │   ├── Post.kt
│   │   │   │   ├── LoginRequest.kt
│   │   │   │   └── LoginResponse.kt
│   │   │   └── network/
│   │   │       ├── ApiResult.kt
│   │   │       ├── ApiResultCallAdapterFactory.kt
│   │   │       ├── ApiService.kt
│   │   │       ├── LoggingInterceptor.kt
│   │   │       ├── RequestIdInterceptor.kt
│   │   │       └── RetrofitClient.kt
│   │   └── AndroidManifest.xml
│   └── test/
│       └── java/com/example/retrofitdemo/network/
│           └── ApiServiceTest.kt
└── build.gradle.kts
```

## Running Tests

The project includes comprehensive unit tests using MockWebServer:

```bash
./gradlew test
```

### Test Coverage

1. **Request Shape Validation**: Verifies path/query parameters are correctly formatted
2. **Header Validation**: Confirms `X-Request-Id` header is added by interceptor
3. **Response<T> Handling**: Tests both 2xx and non-2xx responses
4. **Call<T> Execution**: Validates lazy execution of API calls
5. **Form-Encoded Requests**: Verifies form data encoding
6. **Custom CallAdapter**: Tests `ApiResult` wrapping for success/error/network failure cases

## Key Concepts

### Retrofit Architecture
- **Service Interface**: Defines API endpoints as Kotlin functions
- **Retrofit Instance**: Converts interface methods to HTTP calls
- **CallAdapter**: Transforms return types (e.g., `Call<T>` to `Call<ApiResult<T>>`)
- **Converter**: Serializes/deserializes request/response bodies (Moshi)

### OkHttp Interceptors
Interceptors are executed in order:
1. Application Interceptors (our custom interceptors)
2. OkHttp Internal Interceptors
3. Network Interceptors
4. Actual network call

### Moshi vs Gson
Moshi advantages:
- Kotlin-first design with better null-safety
- Smaller footprint
- Faster reflection-based adapters
- Better error messages

## Source Reading Notes

Throughout the codebase, you'll find detailed comments explaining:
- **Why** certain patterns are used
- **How** Retrofit/OkHttp features work under the hood
- **When** to use different return types (`Call<T>` vs `Response<T>`)
- **Implementation details** of custom adapters and interceptors

## Building the Project

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run specific test class
./gradlew test --tests ApiServiceTest
```

## Dependencies

```kotlin
// Retrofit
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")

// OkHttp
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Moshi
implementation("com.squareup.moshi:moshi:1.15.1")
implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

// Testing
testImplementation("junit:junit:4.13.2")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

## License

This is a demo project for educational purposes.

