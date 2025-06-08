#!/bin/bash

# Pre-commit hook to check for binary files.

# Find the common ancestor between develop and the current branch
base_commit=$(git merge-base 'origin/develop' HEAD)

# Get the list of staged changes (files ready to be committed)
staged_files=$(git diff --cached --name-only)

# Get the list of changed files compared to the base commit
changed_files=$(git diff --name-only "$base_commit" HEAD)

# Combine both lists of files, ensuring no duplicates
all_files=$(echo -e "$staged_files\n$changed_files" | sort -u)

function checkForBinaries() {
  binaryFilesCount=0
  binaryFiles=()

  echo "$all_files" | git check-attr --stdin binary | while IFS=: read -r filename _ status; do
    if [[ "$status" == "set" ]]; then
      ((binaryFilesCount++))
      binaryFiles+=("$filename")
      printf "\n\033[33m%s\033[0m" "$filename"
    fi
  done

  if [[ "$binaryFilesCount" -gt 0 ]]; then
    printf "\n\nPlease remove the %d detected binary file(s)." "$binaryFilesCount"
    printf "\nBINARY FILES CHECK FAILED\n"
    exit 1
  else
    echo "No binary files found in commit"
    echo "BINARY FILES CHECK PASSED"
  fi
}

checkForBinaries
