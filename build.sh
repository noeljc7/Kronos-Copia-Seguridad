#!/usr/bin/env bash

# KRONOS-TV Build Script

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== KRONOS-TV Build System ===${NC}"

# Check if Android SDK is set
if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "⚠️  ANDROID_SDK_ROOT not set. Please set it before building."
    exit 1
fi

# Build tasks
case "${1:-build}" in
    build)
        echo -e "${GREEN}Building APK...${NC}"
        ./gradlew assembleDebug
        ;;
    release)
        echo -e "${GREEN}Building Release APK...${NC}"
        ./gradlew assembleRelease
        ;;
    clean)
        echo -e "${GREEN}Cleaning build files...${NC}"
        ./gradlew clean
        ;;
    test)
        echo -e "${GREEN}Running tests...${NC}"
        ./gradlew test
        ;;
    install)
        echo -e "${GREEN}Installing APK to device/emulator...${NC}"
        ./gradlew installDebug
        ;;
    *)
        echo "Usage: $0 {build|release|clean|test|install}"
        exit 1
        ;;
esac

echo -e "${GREEN}✓ Done!${NC}"
