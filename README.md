
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
│   │   │   ├── network/
│   │   │   │   ├── ApiResult.kt
│   │   │   │   ├── ApiResultCallAdapterFactory.kt
│   │   │   │   ├── ApiService.kt
│   │   │   │   ├── LoggingInterceptor.kt
│   │   │   │   ├── RequestIdInterceptor.kt
│   │   │   │   └── RetrofitClient.kt
│   │   │   └── samples/
│   │   │       ├── HelloRetrofitSample.kt
│   │   │       ├── JsonBodyPostSample.kt
│   │   │       ├── FormAndMultipartSample.kt
│   │   │       └── ReturnTypesComparisonSample.kt
│   │   └── AndroidManifest.xml
│   └── test/
│       └── java/com/example/retrofitdemo/
│           ├── network/
│           │   └── ApiServiceTest.kt
│           └── samples/
│               ├── HelloRetrofitSampleTest.kt
│               ├── JsonBodyPostSampleTest.kt
│               ├── FormAndMultipartSampleTest.kt
│               ├── ReturnTypesComparisonSampleTest.kt
│               ├── FailureMatrixSampleTest.kt
│               ├── CancelPropagationSampleTest.kt
│               └── InterceptorOrderSampleTest.kt
└── build.gradle.kts
```

## Running Tests

The project includes comprehensive unit tests using MockWebServer:

```bash
./gradlew test
```

### Test Coverage

The `ApiServiceTest.kt` includes 9 comprehensive tests:

1. **Path and Query Parameters**: Validates that `@Path` and `@Query` annotations work correctly
2. **Request ID Header**: Confirms `RequestIdInterceptor` adds `X-Request-Id` header with valid UUID
3. **Non-2xx Error Handling**: Tests `Response<T>` wrapper for 404 and other error codes
4. **2xx Success Handling**: Tests `Response<T>` wrapper for successful responses
5. **Call Execution**: Validates `Call<T>` lazy execution pattern
6. **Form Encoding**: Verifies `@FormUrlEncoded` produces correct `Content-Type` and body format
7. **ApiResult Success Wrapping**: Tests custom CallAdapter wraps successful responses in `ApiResult.Success`
8. **ApiResult Error Wrapping**: Tests custom CallAdapter wraps HTTP errors in `ApiResult.Error` with error body
9. **ApiResult Network Failure**: Tests custom CallAdapter wraps network failures in `ApiResult.Error`

Each test uses MockWebServer to simulate API responses without requiring a real server.

### Learning-Aligned Sample Classes

The `samples/` package contains focused examples aligned with learning objectives:

#### 1. HelloRetrofitSample (L1-1, L1-7)
**Demonstrates**: Basic Retrofit setup and GET requests
- Minimal Retrofit instance creation
- Service interface definition
- GET with path parameters (`@Path`)
- GET with query parameters (`@Query`)
- Request ID header injection

**Test Coverage** (`HelloRetrofitSampleTest.kt`):
- ✓ GET request with path parameter
- ✓ Query parameter inclusion
- ✓ X-Request-Id header presence and UUID format
- ✓ Response parsing

#### 2. JsonBodyPostSample (L1-2)
**Demonstrates**: POST requests with JSON body
- Request/Response DTOs
- `@Body` annotation for JSON serialization
- Moshi automatic JSON conversion
- Content-Type: application/json

**Test Coverage** (`JsonBodyPostSampleTest.kt`):
- ✓ POST method verification
- ✓ Content-Type header validation
- ✓ JSON body serialization
- ✓ Response deserialization

#### 3. FormAndMultipartSample (L1-2)
**Demonstrates**: Form-encoded and multipart requests
- `@FormUrlEncoded` with `@Field`
- `@Multipart` with `@Part`
- Different content types
- File upload simulation

**Test Coverage** (`FormAndMultipartSampleTest.kt`):
- ✓ Form-encoded Content-Type
- ✓ Form field encoding
- ✓ Multiple field types (String, Int, Boolean)
- ✓ Multipart Content-Type with boundary
- ✓ Multipart parts structure
- ✓ Boundary separation

#### 4. ReturnTypesComparisonSample (L1-3, L3-7)
**Demonstrates**: Different Retrofit return types
- `Call<T>`: Lazy execution, cancellable
- `suspend T`: Direct result, throws on error
- `suspend Response<T>`: Full response with metadata

**Test Coverage** (`ReturnTypesComparisonSampleTest.kt`):
- ✓ Call<T> success and error inspection
- ✓ suspend T direct result
- ✓ suspend T throws HttpException on non-2xx
- ✓ suspend Response<T> error inspection
- ✓ Side-by-side comparison of all three types

#### 5. FailureMatrixSampleTest (L1-4, L4-7)
**Demonstrates**: Error classification and handling
- HTTP 404 (Client Error) → HttpException
- HTTP 500 (Server Error) → HttpException
- Network failure → IOException
- Malformed JSON → JsonDataException
- Missing fields → JsonDataException
- Wrong data types → JsonDataException

**Test Coverage**:
- ✓ 404 error produces HttpException with code
- ✓ 500 error produces HttpException
- ✓ Network failure produces IOException
- ✓ Malformed JSON produces JsonDataException
- ✓ Missing required field error
- ✓ Wrong data type error
- ✓ Comprehensive error classification strategy

#### 6. CancelPropagationSampleTest (L1-5)
**Demonstrates**: Request cancellation behavior
- `Call.cancel()` for synchronous calls
- Coroutine cancellation for suspend functions
- `withTimeout` for automatic cancellation
- Parent-child cancellation propagation

**Test Coverage**:
- ✓ Call.cancel() cancels network request
- ✓ Cancelled Call throws IOException
- ✓ Coroutine cancellation cancels request
- ✓ withTimeout cancels slow requests
- ✓ Independent cancellation of multiple calls
- ✓ Parent coroutine cancellation propagates to children

#### 7. InterceptorOrderSampleTest (L2-1, L2-2)
**Demonstrates**: Interceptor execution order
- Application interceptors vs. Network interceptors
- Execution order: Application → OkHttp internal → Network → Server
- Invocation counting and tracking
- Logging interceptor pattern

**Test Coverage**:
- ✓ Application interceptors execute before network interceptors
- ✓ Multiple application interceptors execute in order
- ✓ Interceptors invoked once per request
- ✓ Complete chain execution order
- ✓ Logging interceptor request/response tracking

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

### Prerequisites
- Android SDK installed
- `ANDROID_HOME` environment variable set  
- Java 17 or newer
- Internet connection for dependency downloads

### Build Commands

```bash
# Initialize Gradle wrapper (if needed)
gradle wrapper --gradle-version 8.2

# Build the project
./gradlew build

# Run tests
./gradlew test

# Run specific test class
./gradlew test --tests ApiServiceTest

# Run sample tests
./gradlew test --tests "com.example.retrofitdemo.samples.*"

# Run specific sample test
./gradlew test --tests HelloRetrofitSampleTest
./gradlew test --tests FailureMatrixSampleTest

# Clean build
./gradlew clean

# View test report
# After running tests, open: app/build/reports/tests/testDebugUnitTest/index.html
```

### Quick Start Script

Run the included test script to see expected test output:
```bash
./run_tests.sh
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

