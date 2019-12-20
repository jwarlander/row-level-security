#!/bin/bash
readonly assembly="$1"
readonly scripts="${@:2}"
if [[ $# < 2 ]]; then
    echo "usage: $0 <assembled-file> <file-to-be-added> [...]"
    echo
    echo "This script assembles SQL statements from files into one SQL batch file."
    echo "It removes coverage markers and adds statement terminators."
    echo "SQL statements appear in the order of the files in the parameter list."
else
    for script in $scripts ; do
        echo "Adding '$script'"
        sed -e's/ *\[impl->.*\].*//' -e's/^--$//' $script >> "$assembly"
        echo ";" >> "$assembly"
    done
fi