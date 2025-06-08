#!/bin/bash

binary_files=$(git diff --cached --numstat | awk '$1 == "-" && $2 == "-" { print $3 }')
binary_files_count=$(echo "$binary_files" | grep -c . || true)

if [[ "$binary_files_count" -gt 0 ]]; then
  echo -e "\nDetected $binary_files_count binary file(s):"
  echo "$binary_files"
  echo "BINARY FILES CHECK FAILED"
  exit 1
else
  echo "No binary files found in commit"
  echo "BINARY FILES CHECK PASSED"
fi

