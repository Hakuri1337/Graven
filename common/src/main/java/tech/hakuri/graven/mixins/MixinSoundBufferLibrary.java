package tech.hakuri.graven.mixins;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.hakuri.graven.managers.impl.music.Mp3AudioStream;
import tech.hakuri.graven.managers.impl.music.RemoteMusicSource;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public abstract class MixinSoundBufferLibrary {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void graven$openRemoteMusic(Identifier location, boolean looping,
                                        CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!RemoteMusicSource.STREAM_RESOURCE.equals(location)) return;
        RemoteMusicSource.PlaybackRequest request = RemoteMusicSource.request();
        if (request == null) return;
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                return new Mp3AudioStream(request.path(), request.startMs());
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, Util.nonCriticalIoPool()));
    }
}
