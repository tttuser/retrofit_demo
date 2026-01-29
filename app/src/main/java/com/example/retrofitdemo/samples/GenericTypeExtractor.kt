package com.example.retrofitdemo.samples

import retrofit2.Call
import retrofit2.Response
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * GenericTypeExtractor: Demonstrates extracting and inspecting generic types at runtime.
 * 
 * Learning nodes:
 * - L3-3: Understanding Java/Kotlin type system
 * - L3-4: Generic type extraction in Retrofit
 * 
 * This sample demonstrates:
 * 1. Extracting generic types from parameterized types
 * 2. Inspecting Call<T>, Response<T>, and List<T> types
 * 3. Understanding type erasure and its workarounds
 * 4. How Retrofit uses Type information for serialization
 * 
 * Source reading notes:
 * - Java type erasure removes generic type info at runtime
 * - But method signatures preserve it via reflection
 * - Retrofit uses this to know List<User> vs List<Post>
 * - ParameterizedType gives access to type arguments
 */
object GenericTypeExtractor {
    
    /**
     * Sample data classes for testing.
     */
    data class User(val id: Int, val name: String)
    data class Post(val id: Int, val title: String)
    
    /**
     * Extracts the generic type argument from a parameterized type.
     * 
     * Examples:
     * - Call<User> -> User
     * - List<Post> -> Post
     * - Response<String> -> String
     * 
     * @param type The parameterized type to extract from
     * @return The first type argument, or null if not parameterized
     */
    fun extractGenericType(type: Type): Type? {
        return when (type) {
            is ParameterizedType -> {
                // Get the first type argument
                type.actualTypeArguments.firstOrNull()
            }
            else -> null
        }
    }
    
    /**
     * Extracts nested generic types.
     * 
     * Examples:
     * - Call<List<User>> -> [List<User>, User]
     * - Response<List<Post>> -> [List<Post>, Post]
     * 
     * @param type The type to extract from
     * @return List of extracted types (outer to inner)
     */
    fun extractNestedGenericTypes(type: Type): List<Type> {
        val types = mutableListOf<Type>()
        var currentType: Type? = type
        
        while (currentType is ParameterizedType) {
            val typeArg = currentType.actualTypeArguments.firstOrNull()
            if (typeArg != null) {
                types.add(typeArg)
                currentType = typeArg
            } else {
                break
            }
        }
        
        return types
    }
    
    /**
     * Checks if a type is a Call<T>.
     */
    fun isCallType(type: Type): Boolean {
        return type is ParameterizedType && 
               type.rawType == Call::class.java
    }
    
    /**
     * Checks if a type is a Response<T>.
     */
    fun isResponseType(type: Type): Boolean {
        return type is ParameterizedType && 
               type.rawType == Response::class.java
    }
    
    /**
     * Checks if a type is a List<T>.
     */
    fun isListType(type: Type): Boolean {
        return type is ParameterizedType && 
               type.rawType == List::class.java
    }
    
    /**
     * Gets a human-readable description of a type.
     * 
     * Examples:
     * - Call<User> -> "Call<User>"
     * - List<Post> -> "List<Post>"
     * - String -> "String"
     */
    fun getTypeDescription(type: Type): String {
        return when (type) {
            is ParameterizedType -> {
                val rawName = (type.rawType as? Class<*>)?.simpleName ?: type.rawType.toString()
                val args = type.actualTypeArguments.joinToString(", ") { getTypeDescription(it) }
                "$rawName<$args>"
            }
            is Class<*> -> type.simpleName
            else -> type.toString()
        }
    }
    
    /**
     * Extracts the raw class from a type.
     * 
     * Examples:
     * - Call<User> -> Call::class.java
     * - List<Post> -> List::class.java
     * - String -> String::class.java
     */
    fun getRawClass(type: Type): Class<*>? {
        return when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }
    }
    
    /**
     * Example: Extract information from a Retrofit method signature.
     * This simulates what Retrofit does internally.
     */
    fun analyzeRetrofitMethod(returnType: Type): MethodAnalysis {
        val isCall = isCallType(returnType)
        val innerType = extractGenericType(returnType)
        
        return MethodAnalysis(
            returnType = getTypeDescription(returnType),
            usesCall = isCall,
            innerType = innerType?.let { getTypeDescription(it) },
            rawClass = getRawClass(returnType)?.simpleName
        )
    }
    
    /**
     * Result of analyzing a method signature.
     */
    data class MethodAnalysis(
        val returnType: String,
        val usesCall: Boolean,
        val innerType: String?,
        val rawClass: String?
    )
}
