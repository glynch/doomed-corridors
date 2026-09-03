/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.jscene3d.audio.PcmAudio;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes classic Doom DMX format-three unsigned 8-bit mono sound lumps. */
final class DoomDmxSoundDecoder {
    private static final int HEADER_SIZE = 8;
    private static final int DMX_FORMAT = 3;

    private DoomDmxSoundDecoder() {
        throw new AssertionError("DoomDmxSoundDecoder cannot be instantiated");
    }

    /** Converts one complete DMX sound lump into signed 16-bit engine PCM. */
    static PcmAudio decode(byte[] data, String name) {
        if (data.length < HEADER_SIZE) {
            throw invalid(name, "header is shorter than eight bytes");
        }
        ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int format = Short.toUnsignedInt(input.getShort());
        int sampleRate = Short.toUnsignedInt(input.getShort());
        long declaredCount = Integer.toUnsignedLong(input.getInt());
        if (format != DMX_FORMAT) {
            throw invalid(name, "format " + format + " is not supported");
        }
        if (sampleRate == 0) {
            throw invalid(name, "sample rate must be positive");
        }
        if (declaredCount == 0 || declaredCount > input.remaining()) {
            throw invalid(name, "declared sample count is outside the lump");
        }
        int sampleCount = Math.toIntExact(declaredCount);
        short[] samples = new short[sampleCount];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (short) ((Byte.toUnsignedInt(input.get()) - 128) << 8);
        }
        return PcmAudio.mono16(sampleRate, samples);
    }

    /** Creates a source-named malformed-sound failure. */
    private static DoomPatchDataException invalid(String name, String message) {
        return new DoomPatchDataException(name + ": " + message);
    }
}
