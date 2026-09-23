package dev.gulp.platform;

import java.nio.ByteBuffer;

/**
 * Decoders for images (PNG, JPG, WebP), audio (OGG Vorbis, WAV) and fonts (TTF, OTF). Desktop uses stb_image,
 * stb_vorbis and FreeType; the web uses the browser's decoders.
 *
 * <pre>{@code
 * decoders.decodeImage(pngBytes, new PlatformCallback<>() {
 *     public void success(DecodedImage image) { uploadTexture(image); }
 *     public void failure(Throwable error) { useFallbackTexture(); }
 * });
 * }</pre>
 */
public interface PlatformDecoders {

    /**
     * Decodes an image to RGBA.
     *
     * @param encoded file bytes from position to limit
     * @param callback receives the image
     */
    void decodeImage(ByteBuffer encoded, PlatformCallback<DecodedImage> callback);

    /**
     * Decodes a whole audio file to PCM.
     *
     * @param encoded file bytes from position to limit
     * @param callback receives the samples
     */
    void decodeAudio(ByteBuffer encoded, PlatformCallback<DecodedAudio> callback);

    /**
     * Opens a font file for runtime glyph rasterisation. Asynchronous because browsers load fonts in the background.
     *
     * @param fontFile TTF or OTF bytes; the backend copies what it needs
     * @param callback receives the opened face, or {@link IllegalArgumentException} if the bytes are not a font
     */
    void openFont(ByteBuffer fontFile, PlatformCallback<PlatformFontFace> callback);
}
