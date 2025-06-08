#!/bin/bash

# ANSI colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NO_COLOR='\033[0m'

base_commit=$(git merge-base origin/develop HEAD)
binary_files=$(git diff --numstat "$base_commit"...HEAD | awk '$1 == "-" && $2 == "-" { print $3 }')
binary_files_count=$(echo "$binary_files" | grep -c . || true)

if [[ "$binary_files_count" -gt 0 ]]; then
  printf "%s" "Detected $binary_files_count binary file(s):"
  printf "${YELLOW}%s\n""$binary_files"
  printf "\n${RED}%s${NO_COLOR}\n" "BINARY FILES CHECK FAILED"
  exit 1
else
  printf "${GREEN}%s${NO_COLOR}" "No binary files found in commit"
  printf "\n${GREEN}%s${NO_COLOR}\n" "BINARY FILES CHECK PASSED"
fi
