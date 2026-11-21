package com.example.blog.enums;

import java.nio.charset.StandardCharsets;
import io.lettuce.core.protocol.ProtocolKeyword;
public enum BloomCommand implements ProtocolKeyword {
    BF_RESERVE("BF.RESERVE"),
    BF_ADD("BF.ADD"),
    BF_EXISTS("BF.EXISTS"),
    BF_MEXISTS("BF.MEXISTS"),
    BF_MADD("BF.MADD");

    BloomCommand(String name){
        this.command=name;
        this.bytes=name.getBytes(StandardCharsets.US_ASCII);
    }
    private  final byte[] bytes;
    private  final String command;
     @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return command;
    }

}
