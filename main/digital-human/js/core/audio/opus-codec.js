import { log } from '../../utils/logger.js?v=0205';


// Check whether the Opus library is loaded
export function checkOpusLoaded() {
    try {
        // Check whether Module exists (global exported by the local library)
        if (typeof Module === 'undefined') {
            throw new Error('Opus library not loaded, Module object does not exist');
        }

        // Try Module.instance first (exported on the last line of libopus.js)
        if (typeof Module.instance !== 'undefined' && typeof Module.instance._opus_decoder_get_size === 'function') {
            // Replace the global Module object with Module.instance
            window.ModuleInstance = Module.instance;
            log('Opus library loaded (using Module.instance)', 'success');

            // Hide the status after 3 seconds
            const statusElement = document.getElementById('scriptStatus');
            if (statusElement) statusElement.style.display = 'none';
            return;
        }

        // If there is no Module.instance, check the global Module function
        if (typeof Module._opus_decoder_get_size === 'function') {
            window.ModuleInstance = Module;
            log('Opus library loaded (using global Module)', 'success');

            // Hide the status after 3 seconds
            const statusElement = document.getElementById('scriptStatus');
            if (statusElement) statusElement.style.display = 'none';
            return;
        }

        throw new Error('Opus decode functions not found; the Module structure may be incorrect');
    } catch (err) {
        log(`Failed to load the Opus library, please check that libopus.js exists and is correct: ${err.message}`, 'error');
    }
}


// Create an Opus encoder
let opusEncoder = null;
export function initOpusEncoder() {
    try {
        if (opusEncoder) {
            return opusEncoder; // Already initialized
        }

        if (!window.ModuleInstance) {
            log('Cannot create Opus encoder: ModuleInstance unavailable', 'error');
            return;
        }

        // Initialize an Opus encoder
        const mod = window.ModuleInstance;
        const sampleRate = 16000; // 16kHz sample rate
        const channels = 1;       // Mono
        const application = 2048; // OPUS_APPLICATION_VOIP = 2048

        // Create the encoder
        opusEncoder = {
            channels: channels,
            sampleRate: sampleRate,
            frameSize: 960, // 60ms @ 16kHz = 60 * 16 = 960 samples
            maxPacketSize: 4000, // Maximum packet size
            module: mod,

            // Initialize the encoder
            init: function () {
                try {
                    // Get the encoder size
                    const encoderSize = mod._opus_encoder_get_size(this.channels);
                    log(`Opus encoder size: ${encoderSize} bytes`, 'info');

                    // Allocate memory
                    this.encoderPtr = mod._malloc(encoderSize);
                    if (!this.encoderPtr) {
                        throw new Error("Unable to allocate encoder memory");
                    }

                    // Initialize the encoder
                    const err = mod._opus_encoder_init(
                        this.encoderPtr,
                        this.sampleRate,
                        this.channels,
                        application
                    );

                    if (err < 0) {
                        throw new Error(`Opus encoder initialization failed: ${err}`);
                    }

                    // Set bitrate (16kbps)
                    mod._opus_encoder_ctl(this.encoderPtr, 4002, 16000); // OPUS_SET_BITRATE

                    // Set complexity (0-10; higher means better quality but more CPU)
                    mod._opus_encoder_ctl(this.encoderPtr, 4010, 5);     // OPUS_SET_COMPLEXITY

                    // Enable DTX (do not transmit silent frames)
                    mod._opus_encoder_ctl(this.encoderPtr, 4016, 1);     // OPUS_SET_DTX

                    log("Opus encoder initialized", 'success');
                    return true;
                } catch (error) {
                    if (this.encoderPtr) {
                        mod._free(this.encoderPtr);
                        this.encoderPtr = null;
                    }
                    log(`Opus encoder initialization failed: ${error.message}`, 'error');
                    return false;
                }
            },

            // Encode PCM data to Opus
            encode: function (pcmData) {
                if (!this.encoderPtr) {
                    if (!this.init()) {
                        return null;
                    }
                }

                try {
                    const mod = this.module;

                    // Allocate memory for PCM data
                    const pcmPtr = mod._malloc(pcmData.length * 2); // 2 bytes per int16

                    // Copy PCM data to the HEAP
                    for (let i = 0; i < pcmData.length; i++) {
                        mod.HEAP16[(pcmPtr >> 1) + i] = pcmData[i];
                    }

                    // Allocate memory for the output
                    const outPtr = mod._malloc(this.maxPacketSize);

                    // Encode
                    const encodedLen = mod._opus_encode(
                        this.encoderPtr,
                        pcmPtr,
                        this.frameSize,
                        outPtr,
                        this.maxPacketSize
                    );

                    if (encodedLen < 0) {
                        throw new Error(`Opus encoding failed: ${encodedLen}`);
                    }

                    // Copy the encoded data
                    const opusData = new Uint8Array(encodedLen);
                    for (let i = 0; i < encodedLen; i++) {
                        opusData[i] = mod.HEAPU8[outPtr + i];
                    }

                    // Free memory
                    mod._free(pcmPtr);
                    mod._free(outPtr);

                    return opusData;
                } catch (error) {
                    log(`Opus encoding error: ${error.message}`, 'error');
                    return null;
                }
            },

            // Destroy the encoder
            destroy: function () {
                if (this.encoderPtr) {
                    this.module._free(this.encoderPtr);
                    this.encoderPtr = null;
                }
            }
        };

        opusEncoder.init();
        return opusEncoder;
    } catch (error) {
        log(`Failed to create Opus encoder: ${error.message}`, 'error');
        return false;
    }
}