#!/usr/bin/env bash

if [ -z "$1" ]; then
    echo "Usage: $0 <topic_name>"
    exit 1
fi

docker run --rm --net=host public.ecr.aws/soldevelo/kafka:4.2.0 \
kafka-topics.sh --bootstrap-server localhost:9092 \
--create --topic "$1"
