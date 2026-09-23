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
     * Opens a font file for runtime glyph rasterisation.
     *
     * @param fontFile TTF or OTF bytes; the buffer must stay alive until the face is disposed
     * @return the opened face
     * @throws IllegalArgumentException if the bytes are not a supported font
     */
    PlatformFontFace openFont(ByteBuffer fontFile);
}
