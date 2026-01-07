#!/bin/bash

# Defines color codes for output formatting

# Red color for error messages
RED='\033[0;31m'

# Green color for success messages
GREEN='\033[0;32m'

# Yellow color for warning messages
YELLOW='\033[0;33m'

# No color, resets color after message
NC='\033[0m'

# Function to print an error message in red
function echo_error() {
    printf "${RED}%b${NC}\n" "$1"
}

# Function to print a success message in green
function echo_success() {
    printf "${GREEN}%b${NC}\n" "$1"
}

# Function to print a warning message in yellow
function echo_warning() {
    printf "${YELLOW}%b${NC}\n" "$1"
}
