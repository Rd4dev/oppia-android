#!/bin/bash

source scripts/formatting.sh

check_binary_files() {
  echo "*********************************"
  echo "Checking for Binary file presence"
  echo "*********************************"

  base_commit=$(git merge-base origin/develop HEAD)
  binary_files=$(git diff --numstat "$base_commit"...HEAD | awk '$1 == "-" && $2 == "-" { print $3 }')
  binary_files_count=$(echo "$binary_files" | grep -c . || true)

  if [[ "$binary_files_count" -gt 0 ]]; then
    echo "Detected $binary_files_count binary file(s):"
    echo "$binary_files"
    echo_error "\nBINARY FILES CHECK FAILED"
    exit 1
  else
    echo "No binary files found in commit"
    echo_success "\nBINARY FILES CHECK PASSED"
  fi
}

check_binary_files
