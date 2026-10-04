// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/** Independent UCIP stream. Never fed into the WAVF decoder. */
final class ClipboardWire {
    static final int MAX_BYTES = 262144;
    static final int TEXT = 1, REQUEST = 2;
    static final class Message {
        final int type;
        final String text;
        Message(int type, String text) { this.type = type; this.text = text; }
    }
    static byte[] encode(int type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if ((type != TEXT && type != REQUEST) || bytes.length > MAX_BYTES ||
            (type == REQUEST && bytes.length != 0)) throw new IOException("Invalid clipboard packet");
        return ByteBuffer.allocate(12 + bytes.length).order(ByteOrder.LITTLE_ENDIAN)
            .put(new byte[]{'U','C','I','P'}).put((byte)1).put((byte)type)
            .putShort((short)0).putInt(bytes.length).put(bytes).array();
    }
    static Message read(InputStream in) throws IOException {
        byte[] header = new byte[12];
        new DataInputStream(in).readFully(header);
        ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (h.getInt() != 0x50494355 || h.get() != 1) throw new IOException("Invalid clipboard header");
        int type = h.get() & 255;
        int reserved = h.getShort() & 65535, size = h.getInt();
        if (reserved != 0 || size < 0 || size > MAX_BYTES ||
            (type != TEXT && type != REQUEST) || (type == REQUEST && size != 0))
            throw new IOException("Invalid clipboard size/type");
        byte[] bytes = new byte[size];
        new DataInputStream(in).readFully(bytes);
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            return new Message(type, text);
        } catch (CharacterCodingException error) { throw new IOException("Invalid clipboard UTF-8", error); }
    }
}
