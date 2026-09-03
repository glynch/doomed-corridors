/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.glynch.jscene3d.audio.PcmAudio;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

/** Specifies conversion from classic DMX unsigned samples to generic engine PCM. */
final class DoomDmxSoundDecoderTest {
    /** Converts format metadata and recenters unsigned bytes as signed 16-bit samples. */
    @Test
    void decodesFormatThreeMonoSound() {
        byte[] data = sound(11_025, 0, 128, 255);

        PcmAudio audio = DoomDmxSoundDecoder.decode(data, "DSPISTOL");

        assertThat(audio.channels()).isOne();
        assertThat(audio.sampleRate()).isEqualTo(11_025);
        assertThat(audio.frameCount()).isEqualTo(3);
        assertThat(audio.samples()).containsExactly(Short.MIN_VALUE, 0, 32_512);
    }

    /** Rejects a declared sample range extending beyond the source lump. */
    @Test
    void rejectsTruncatedSamples() {
        byte[] data = sound(8_000, 128);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 2);

        assertThatThrownBy(() -> DoomDmxSoundDecoder.decode(data, "BROKEN"))
                .isInstanceOf(DoomPatchDataException.class)
                .hasMessageContaining("declared sample count");
    }

    /** Builds one little-endian DMX sound fixture. */
    private static byte[] sound(int sampleRate, int... samples) {
        ByteBuffer data = ByteBuffer.allocate(8 + samples.length).order(ByteOrder.LITTLE_ENDIAN);
        data.putShort((short) 3);
        data.putShort((short) sampleRate);
        data.putInt(samples.length);
        for (int sample : samples) {
            data.put((byte) sample);
        }
        return data.array();
    }
}
