import BlockingQueue from '../../utils/blocking-queue.js?v=0205';
import { log } from '../../utils/logger.js?v=0205';

// Audio streaming playback context class
export class StreamingContext {
    constructor(opusDecoder, audioContext, sampleRate, channels, minAudioDuration) {
        this.opusDecoder = opusDecoder;
        this.audioContext = audioContext;

        // Audio parameters
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.minAudioDuration = minAudioDuration;

        // Initialize queues and state
        this.queue = [];          // Decoded PCM queue currently playing
        this.activeQueue = new BlockingQueue(); // Decoded PCM queue ready to play
        this.pendingAudioBufferQueue = [];  // Pending buffer queue
        this.audioBufferQueue = new BlockingQueue();  // Buffer queue
        this.playing = false;     // Whether playback is in progress
        this.endOfStream = false; // Whether the end-of-stream signal was received
        this.source = null;       // Current audio source
        this.totalSamples = 0;    // Accumulated total samples
        this.lastPlayTime = 0;    // Timestamp of the last playback
        this.scheduledEndTime = 0; // End time of scheduled audio

        // Initialize the analyser node (used by Live2D)
        this.analyser = this.audioContext.createAnalyser();
        this.analyser.fftSize = 256;
    }

    // Buffer audio arrays
    pushAudioBuffer(item) {
        this.audioBufferQueue.enqueue(...item);
    }

    // Get the buffer queue to process; single-threaded, so continuous audioBufferQueue updates are safe
    async getPendingAudioBufferQueue() {
        // Wait for data to arrive and take it
        const data = await this.audioBufferQueue.dequeue();
        // Assign to the pending queue
        this.pendingAudioBufferQueue = data;
    }

    // Get the decoded PCM queue being played; single-threaded, so continuous activeQueue updates are safe
    async getQueue(minSamples) {
        const num = minSamples - this.queue.length > 0 ? minSamples - this.queue.length : 1;

        // Wait for data and take it
        const tempArray = await this.activeQueue.dequeue(num);
        this.queue.push(...tempArray);
    }

    // Convert Int16 audio data to Float32 audio data
    convertInt16ToFloat32(int16Data) {
        const float32Data = new Float32Array(int16Data.length);
        for (let i = 0; i < int16Data.length; i++) {
            // Map [-32768,32767] to [-1,1], always dividing by 32768.0 to avoid asymmetric distortion
            float32Data[i] = int16Data[i] / 32768.0;
        }
        return float32Data;
    }

    // Get the number of packets pending decode
    getPendingDecodeCount() {
        return this.audioBufferQueue.length + this.pendingAudioBufferQueue.length;
    }

    // Get the number of samples pending playback (converted to packets, 960 samples each)
    getPendingPlayCount() {
        // Count samples already in the queue
        const queuedSamples = this.activeQueue.length + this.queue.length;

        // Count samples scheduled but not yet played (in the Web Audio buffer)
        let scheduledSamples = 0;
        if (this.playing && this.scheduledEndTime) {
            const currentTime = this.audioContext.currentTime;
            const remainingTime = Math.max(0, this.scheduledEndTime - currentTime);
            scheduledSamples = Math.floor(remainingTime * this.sampleRate);
        }

        const totalSamples = queuedSamples + scheduledSamples;
        return Math.ceil(totalSamples / 960);
    }

    // Clear all audio buffers
    clearAllBuffers() {
        log('Clearing all audio buffers', 'info');

        // Clear all queues (clear() keeps the object references)
        this.audioBufferQueue.clear();
        this.pendingAudioBufferQueue = [];
        this.activeQueue.clear();
        this.queue = [];

        // Stop the currently playing audio source
        if (this.source) {
            try {
                this.source.stop();
                this.source.disconnect();
            } catch (e) {
                // Ignore errors from already-stopped sources
            }
            this.source = null;
        }

        // Reset state
        this.playing = false;
        this.scheduledEndTime = this.audioContext.currentTime;
        this.totalSamples = 0;

        log('Audio buffers cleared', 'success');
    }

    // Get the analyser node (used by Live2D)
    getAnalyser() {
        return this.analyser;
    }

    // Decode Opus data to PCM
    async decodeOpusFrames() {
        if (!this.opusDecoder) {
            log('Opus decoder not initialized, cannot decode', 'error');
            return;
        } else {
            log('Opus decoder started', 'info');
        }

        while (true) {
            let decodedSamples = [];
            for (const frame of this.pendingAudioBufferQueue) {
                try {
                    // Decode with the Opus decoder
                    const frameData = this.opusDecoder.decode(frame);
                    if (frameData && frameData.length > 0) {
                        // Convert to Float32
                        const floatData = this.convertInt16ToFloat32(frameData);
                        // Use a loop instead of the spread operator
                        for (let i = 0; i < floatData.length; i++) {
                            decodedSamples.push(floatData[i]);
                        }
                    }
                } catch (error) {
                    log("Opus decoding failed: " + error.message, 'error');
                }
            }

            if (decodedSamples.length > 0) {
                // Use a loop instead of the spread operator
                for (let i = 0; i < decodedSamples.length; i++) {
                    this.activeQueue.enqueue(decodedSamples[i]);
                }
                this.totalSamples += decodedSamples.length;
            } else {
                log('No samples decoded successfully', 'warning');
            }
            await this.getPendingAudioBufferQueue();
        }
    }

    // Start audio playback
    async startPlaying() {
        this.scheduledEndTime = this.audioContext.currentTime; // Track the end time of scheduled audio

        while (true) {
            // Initial buffering: wait for enough samples before starting playback
            const minSamples = this.sampleRate * this.minAudioDuration * 2;
            if (!this.playing && this.queue.length < minSamples) {
                await this.getQueue(minSamples);
            }
            this.playing = true;

            // Keep playing queued audio, one small chunk at a time
            while (this.playing && this.queue.length > 0) {
                // Play 120ms of audio each time (2 Opus packets)
                const playDuration = 0.12;
                const targetSamples = Math.floor(this.sampleRate * playDuration);
                const actualSamples = Math.min(this.queue.length, targetSamples);

                if (actualSamples === 0) break;

                const currentSamples = this.queue.splice(0, actualSamples);
                const audioBuffer = this.audioContext.createBuffer(this.channels, currentSamples.length, this.sampleRate);
                audioBuffer.copyToChannel(new Float32Array(currentSamples), 0);

                // Create the audio source
                this.source = this.audioContext.createBufferSource();
                this.source.buffer = audioBuffer;

                // Schedule playback time precisely
                const currentTime = this.audioContext.currentTime;
                const startTime = Math.max(this.scheduledEndTime, currentTime);

                // Connect to the analyser and output
                this.source.connect(this.analyser);
                this.source.connect(this.audioContext.destination);

                log(`Scheduled ${currentSamples.length} samples for playback, about ${(currentSamples.length / this.sampleRate).toFixed(2)} s`, 'debug');
                this.source.start(startTime);

                // Update the schedule time for the next audio chunk
                const duration = audioBuffer.duration;
                this.scheduledEndTime = startTime + duration;
                this.lastPlayTime = startTime;

                // If the queue does not have enough data, wait for new data
                if (this.queue.length < targetSamples) {
                    break;
                }
            }

            // Wait for new data
            await this.getQueue(minSamples);
        }
    }
}

// Factory function for creating a StreamingContext instance
export function createStreamingContext(opusDecoder, audioContext, sampleRate, channels, minAudioDuration) {
    return new StreamingContext(opusDecoder, audioContext, sampleRate, channels, minAudioDuration);
}