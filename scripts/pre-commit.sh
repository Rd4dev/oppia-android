#!/bin/bash

# Pre-commit hook to check for binary files in the staged changes.

function checkForBinaries() {
    binaryFilesCount=0

    for file in $(git diff --cached --name-only --diff-filter=d) ; do
        binaryFlag=$(file --mime "${file}" | grep binary)
        if [[ -n "${binaryFlag}" ]] ; then
            binaryFiles="${binaryFiles}\n$file"
            (( binaryFilesCount+=1 ))
        fi
    done

    if [[ -n "${binaryFiles}" && "${binaryFilesCount}" -gt 0 ]] ; then
        printf "\nFound the following binary files: \n\t"
        printf "\033[33m%b\033[0m\n\n" "$binaryFiles"
        printf "Please remove the binary files before committing.\n"
        exit 1
    fi
}

checkForBinaries
