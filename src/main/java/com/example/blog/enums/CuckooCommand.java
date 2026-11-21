package com.example.blog.enums;

import java.nio.charset.StandardCharsets;

import io.lettuce.core.protocol.ProtocolKeyword;

public enum CuckooCommand implements ProtocolKeyword {
     CF_RESERVE("CF.RESERVE"),
    CF_ADD("CF.ADD"),
    CF_EXISTS("CF.EXISTS"),
    CF_DEL("CF.DEL"),
    CF_MADD("CF.MADD");

    private final byte[] bytes;
    private final String command;

    CuckooCommand(String name) {
        this.command = name;
        this.bytes = name.getBytes(StandardCharsets.US_ASCII);
    }

    @Override public byte[] getBytes() { return bytes; }
    @Override public String toString() { return command; }
}
