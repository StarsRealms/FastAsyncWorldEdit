package com.fastasyncworldedit.core.queue;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IBatchProcessorTest {

    @Nested
    @Isolated
    class trimY {

        private static final int[] CHUNK_DATA = new int[16 * 16 * 16];
        private static final int[] SLICE_AIR = new int[16 * 16];
        private static final int[] SLICE_RESERVED = new int[16 * 16];
        private final IBatchProcessor processor = new NoopBatchProcessor();

        static {
            Arrays.fill(CHUNK_DATA, (int) BlockTypesCache.ReservedIDs.AIR);
            Arrays.fill(SLICE_AIR, (int) BlockTypesCache.ReservedIDs.AIR);
            Arrays.fill(SLICE_RESERVED, (int) BlockTypesCache.ReservedIDs.__RESERVED__);
        }

        @ParameterizedTest
        @MethodSource("provideTrimYInBoundsParameters")
        void testFullChunkSelectedInBoundedRegion(int minY, int maxY, int minSection, int maxSection) {
            final IChunkSet set = mock();

            int[][] sections = new int[(320 + 64) >> 4][CHUNK_DATA.length];
            for (final int[] chars : sections) {
                System.arraycopy(CHUNK_DATA, 0, chars, 0, CHUNK_DATA.length);
            }

            when(set.getMinSectionPosition()).thenReturn(-64 >> 4);
            when(set.getMaxSectionPosition()).thenReturn(319 >> 4);
            when(set.hasSection(anyInt())).thenReturn(true);
            when(set.loadIfPresent(anyInt())).thenAnswer(invocationOnMock -> sections[invocationOnMock.<Integer>getArgument(0) + 4]);
            doAnswer(invocationOnMock -> {
                sections[invocationOnMock.<Integer>getArgument(0) + 4] = invocationOnMock.getArgument(1);
                return null;
            }).when(set).setBlocks(anyInt(), any());

            processor.trimY(set, minY, maxY, true);


            for (int section = -64 >> 4; section < 320 >> 4; section++) {
                int sectionIndex = section + 4;
                int[] palette = sections[sectionIndex];
                if (section < minSection) {
                    assertNull(palette, "expected section below minimum section to be null");
                    continue;
                }
                if (section > maxSection) {
                    assertNull(palette, "expected section above maximum section to be null");
                    continue;
                }
                if (section == minSection) {
                    for (int slice = 0; slice < 16; slice++) {
                        boolean shouldContainBlocks = slice >= (minY % 16);
                        // If boundaries only span one section, the upper constraints have to be checked explicitly
                        if (section == maxSection) {
                            shouldContainBlocks &= slice <= (maxY % 16);
                        }
                        assertArrayEquals(
                                shouldContainBlocks ? SLICE_AIR : SLICE_RESERVED,
                                Arrays.copyOfRange(palette, slice << 8, (slice + 1) << 8),
                                ("[lower] slice %d (y=%d) expected to contain " + (shouldContainBlocks ? "air" : "nothing"))
                                        .formatted(slice, ((section << 4) + slice))
                        );
                    }
                    continue;
                }
                if (section == maxSection) {
                    for (int slice = 0; slice < 16; slice++) {
                        boolean shouldContainBlocks = slice <= (maxY % 16);
                        assertArrayEquals(
                                shouldContainBlocks ? SLICE_AIR : SLICE_RESERVED,
                                Arrays.copyOfRange(palette, slice << 8, (slice + 1) << 8),
                                ("[upper] slice %d (y=%d) expected to contain " + (shouldContainBlocks ? "air" : "nothing"))
                                        .formatted(slice, ((section << 4) + slice))
                        );
                    }
                    continue;
                }
                assertArrayEquals(CHUNK_DATA, palette, "full captured chunk @ %d should contain full data".formatted(section));
            }

        }

        /**
         * Arguments explained:
         * 1. minimum y coordinate (inclusive)
         * 2. maximum y coordinate (inclusive)
         * 3. chunk section which contains minimum y coordinate
         * 4. chunk section which contains maximum y coordinate
         */
        private static Stream<Arguments> provideTrimYInBoundsParameters() {
            return Stream.of(
                    Arguments.of(64, 72, 4, 4),
                    Arguments.of(-64, 0, -4, 0),
                    Arguments.of(0, 128, 0, 8),
                    Arguments.of(16, 132, 1, 8),
                    Arguments.of(4, 144, 0, 9),
                    Arguments.of(12, 255, 0, 15),
                    Arguments.of(24, 103, 1, 6)
            );
        }

    }

    private static final class NoopBatchProcessor implements IBatchProcessor {

        @Override
        public IChunkSet processSet(final IChunk chunk, final IChunkGet get, final IChunkSet set) {
            return set;
        }

        @Override
        public @Nullable Extent construct(final Extent child) {
            return null;
        }

    }

}
