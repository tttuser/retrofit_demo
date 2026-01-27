#!/bin/bash

# Retrofit Demo - Build and Test Script
# This script demonstrates how to build and test the Retrofit demo project

echo "============================================"
echo "Retrofit + Moshi Android Demo Project"
echo "============================================"
echo ""

echo "Prerequisites:"
echo "- Android SDK installed"
echo "- ANDROID_HOME environment variable set"
echo "- Java 17 or newer"
echo "- Internet connection for dependency downloads"
echo ""

echo "Step 1: Clean build"
echo "-------------------"
echo "Command: ./gradlew clean"
echo ""

echo "Step 2: Build the project"
echo "------------------------"
echo "Command: ./gradlew build"
echo ""

echo "Step 3: Run unit tests"
echo "---------------------"
echo "Command: ./gradlew test"
echo ""

echo "Expected test results:"
echo "- ApiServiceTest.kt should run 9 test cases:"
echo "  1. ✓ getUser sends correct path and query parameters"
echo "  2. ✓ requests include X-Request-Id header"  
echo "  3. ✓ getUserWithResponse handles non-2xx responses correctly"
echo "  4. ✓ getUserWithResponse handles 2xx responses correctly"
echo "  5. ✓ getPosts returns Call that can be executed"
echo "  6. ✓ login sends form-encoded body"
echo "  7. ✓ getUserAsApiResult wraps success response in ApiResult.Success"
echo "  8. ✓ getUserAsApiResult wraps error response in ApiResult.Error"
echo "  9. ✓ getUserAsApiResult wraps network failure in ApiResult.Error"
echo ""

echo "Step 4: View test report"
echo "-----------------------"
echo "Test report will be available at:"
echo "  app/build/reports/tests/testDebugUnitTest/index.html"
echo ""

echo "Step 5: Run specific test"
echo "------------------------"
echo "Command: ./gradlew test --tests ApiServiceTest"
echo ""

echo "To actually run the tests, execute:"
echo "  ./gradlew test"
echo ""
