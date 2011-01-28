#!/bin/bash

[ -d gen ] || mkdir gen
find "$2" -name "*.proto" -exec protoc --proto_path="$1" --java_out="$3" "{}" \;


