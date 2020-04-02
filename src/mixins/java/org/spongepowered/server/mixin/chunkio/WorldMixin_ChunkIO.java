/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.server.mixin.chunkio;

import net.minecraft.world.chunk.IChunkProvider;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.world.storage.SpongeChunkLayout;
import org.spongepowered.server.bridge.world.chunkio.ChunkIOProviderBridge_Vanilla;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(net.minecraft.world.World.class)
public abstract class WorldMixin_ChunkIO implements World {

    @Shadow protected IChunkProvider chunkProvider;

    @Override
    public CompletableFuture<Optional<Chunk>> loadChunkAsync(int cx, int cy, int cz, boolean shouldGenerate) {
        // Currently, we can only load asynchronously if the chunk should not be generated
        if (shouldGenerate) {
            return World.super.loadChunkAsync(cx, cy, cz, true);
        }

        if (!SpongeChunkLayout.instance.isValidChunk(cx, cy, cz)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Optional<Chunk>> future = new CompletableFuture<>();
        ((ChunkIOProviderBridge_Vanilla) this.chunkProvider).vanillaBridge$loadChunk(cx, cz,
                chunk -> future.complete(Optional.ofNullable((Chunk) chunk)));
        return future;
    }

}
