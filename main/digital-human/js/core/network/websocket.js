// WebSocket message handling module
import { getConfig, saveConnectionUrls } from '../../config/manager.js?v=0205';
import { uiController } from '../../ui/controller.js?v=0205';
import { log } from '../../utils/logger.js?v=0205';
import { getAudioPlayer } from '../audio/player.js?v=0205';
import { getAudioRecorder } from '../audio/recorder.js?v=0205';
import { executeMcpTool, getMcpTools, setWebSocket as setMcpWebSocket } from '../mcp/tools.js?v=0205';
import { webSocketConnect } from './ota-connector.js?v=0205';

// WebSocket handler class
export class WebSocketHandler {
    constructor() {
        this.websocket = null;
        this.onConnectionStateChange = null;
        this.onRecordButtonStateChange = null;
        this.onSessionStateChange = null;
        this.onSessionEmotionChange = null;
        this.onChatMessage = null; // Added: chat message callback
        this.currentSessionId = null;
        this.isRemoteSpeaking = false;
    }

    // Send the hello handshake message
    async sendHelloMessage() {
        if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return false;

        try {
            const config = getConfig();

            const helloMessage = {
                type: 'hello',
                device_id: config.deviceId,
                device_name: config.deviceName,
                device_mac: config.deviceMac,
                token: config.token,
                features: {
                    mcp: true,
                    emoji: config.emojiEnabled
                }
            };

            log('Sending hello handshake message', 'info');
            this.websocket.send(JSON.stringify(helloMessage));

            return new Promise(resolve => {
                const timeout = setTimeout(() => {
                    log('Timed out waiting for hello response', 'error');
                    log('Tip: try clicking the "Test Authentication" button to troubleshoot the connection', 'info');
                    resolve(false);
                }, 5000);

                const onMessageHandler = (event) => {
                    try {
                        const response = JSON.parse(event.data);
                        if (response.type === 'hello' && response.session_id) {
                            log(`Server handshake succeeded, session ID: ${response.session_id}`, 'success');
                            clearTimeout(timeout);
                            this.websocket.removeEventListener('message', onMessageHandler);
                            resolve(true);
                        }
                    } catch (e) {
                        // Ignore non-JSON messages
                    }
                };

                this.websocket.addEventListener('message', onMessageHandler);
            });
        } catch (error) {
            log(`Error sending hello message: ${error.message}`, 'error');
            return false;
        }
    }

    _sendWakeupMessages(sessionId) {
        if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;

        // listen detect
        this.websocket.send(JSON.stringify({
            session_id: sessionId,
            type: 'listen',
            state: 'detect',
            text: '嘿，你好呀'
        }));
        log('Sent listen detect message, wake word: 嘿，你好呀', 'info');

        // listen start: start listening
        this.websocket.send(JSON.stringify({
            session_id: sessionId,
            type: 'listen',
            state: 'start',
            mode: 'auto'
        }));
        log('Sent listen start message', 'info');
    }

    // Handle text messages
    handleTextMessage(message) {
        if (message.type === 'hello') {
            log(`Server response: ${JSON.stringify(message, null, 2)}`, 'success');
            window.cameraAvailable = true;
            log('Connected, camera is available', 'success');
            uiController.updateDialButton(true);

            this._sendWakeupMessages(message.session_id);

            uiController.startAIChatSession();
        } else if (message.type === 'tts') {
            this.handleTTSMessage(message);
        } else if (message.type === 'audio') {
            log(`Received audio control message: ${JSON.stringify(message)}`, 'info');
        } else if (message.type === 'stt') {
            log(`Recognition result: ${message.text}`, 'info');
            // Check whether the device needs to be bound (the Chinese keyword matches server text, keep it)
            if (message.text && (message.text.includes('绑定') || message.text.includes('bind'))) {
                log('Received device bind prompt, updating camera state', 'warning');
                window.cameraAvailable = false;
                // Turn off the camera
                if (typeof window.stopCamera === 'function') {
                    window.stopCamera();
                }
                // Update camera button state
                const cameraBtn = document.getElementById('cameraBtn');
                if (cameraBtn) {
                    cameraBtn.classList.remove('camera-active');
                    cameraBtn.querySelector('.btn-text').textContent = 'Camera';
                    cameraBtn.disabled = true;
                    cameraBtn.title = 'Please bind the verification code first';
                }
            }
            // Show STT messages via the new chat message callback
            if (this.onChatMessage && message.text) {
                this.onChatMessage(message.text, true);
            }
        } else if (message.type === 'llm') {
            log(`LLM reply: ${message.text}`, 'info');
            // Show the LLM reply via the new chat message callback
            if (this.onChatMessage && message.text) {
                this.onChatMessage(message.text, false);
            }

            // If it contains an emoji, update the sessionStatus emoji and trigger a Live2D motion
            if (message.text && /[\u{1F300}-\u{1F9FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]/u.test(message.text)) {
                // Extract the emoji
                const emojiMatch = message.text.match(/[\u{1F300}-\u{1F9FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]/u);
                if (emojiMatch && this.onSessionEmotionChange) {
                    this.onSessionEmotionChange(emojiMatch[0]);
                }

                // Trigger the Live2D emotion motion
                if (message.emotion) {
                    console.log(`Received emotion message: emotion=${message.emotion}, text=${message.text}`);
                    this.triggerLive2DEmotionAction(message.emotion);
                }
            }

            // Only add to the conversation when the text is not just an emoji
            // Check whether anything remains after removing emojis
            const textWithoutEmoji = message.text ? message.text.replace(/[\u{1F300}-\u{1F9FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]/gu, '').trim() : '';
            if (textWithoutEmoji && this.onChatMessage) {
                this.onChatMessage(message.text, false);
            }
        } else if (message.type === 'mcp') {
            this.handleMCPMessage(message);
        } else {
            log(`Unknown message type: ${message.type}`, 'info');
            if (this.onChatMessage) {
                this.onChatMessage(`Unknown message type: ${message.type}\n${JSON.stringify(message, null, 2)}`, false);
            }
        }
    }

    // Handle TTS messages
    handleTTSMessage(message) {
        if (message.state === 'start') {
            log('Server started sending audio', 'info');
            this.currentSessionId = message.session_id;
            this.isRemoteSpeaking = true;
            if (this.onSessionStateChange) {
                this.onSessionStateChange(true);
            }

            // Start the Live2D talking animation
            this.startLive2DTalking();
        } else if (message.state === 'sentence_start') {
            log(`Server sent audio segment: ${message.text}`, 'info');
            this.ttsSentenceCount = (this.ttsSentenceCount || 0) + 1;

            if (message.text && this.onChatMessage) {
                this.onChatMessage(message.text, false);
            }

            // Make sure the animation is running at sentence start
            const live2dManager = window.chatApp?.live2dManager;
            if (live2dManager && !live2dManager.isTalking) {
                this.startLive2DTalking();
            }
        } else if (message.state === 'sentence_end') {
            log(`Audio segment ended: ${message.text}`, 'info');

            // Do not stop the animation at sentence end; wait for the next sentence or the final stop
        } else if (message.state === 'stop') {
            log('Server audio transfer ended, clearing all audio buffers', 'info');

            // Clear all audio buffers and stop playback
            const audioPlayer = getAudioPlayer();
            audioPlayer.clearAllAudio();

            this.isRemoteSpeaking = false;
            if (this.onRecordButtonStateChange) {
                this.onRecordButtonStateChange(false);
            }
            if (this.onSessionStateChange) {
                this.onSessionStateChange(false);
            }

            // Delay stopping the Live2D talking animation so all sentences finish playing
            setTimeout(() => {
                this.stopLive2DTalking();
                this.ttsSentenceCount = 0; // Reset the counter
            }, 1000); // 1-second delay to ensure all sentences are done
        }
    }

    // Start the Live2D talking animation
    startLive2DTalking() {
        try {
            // Get the Live2D manager instance
            const live2dManager = window.chatApp?.live2dManager;
            if (live2dManager && live2dManager.live2dModel) {
                // Use the audio player's analyser node
                live2dManager.startTalking();
                log('Live2D talking animation started', 'info');
            }
        } catch (error) {
            log(`Failed to start Live2D talking animation: ${error.message}`, 'error');
        }
    }

    // Stop the Live2D talking animation
    stopLive2DTalking() {
        try {
            const live2dManager = window.chatApp?.live2dManager;
            if (live2dManager) {
                live2dManager.stopTalking();
                log('Live2D talking animation stopped', 'info');
            }
        } catch (error) {
            log(`Failed to stop Live2D talking animation: ${error.message}`, 'error');
        }
    }

    // Initialize the Live2D audio analyser
    initializeLive2DAudioAnalyzer() {
        try {
            const live2dManager = window.chatApp?.live2dManager;
            if (live2dManager) {
                // Initialize the audio analyser (using the audio player's context)
                if (live2dManager.initializeAudioAnalyzer()) {
                    log('Live2D audio analyser initialized and connected to the audio player', 'success');
                } else {
                    log('Live2D audio analyser initialization failed, falling back to simulated animation', 'warning');
                }
            }
        } catch (error) {
            log(`Failed to initialize Live2D audio analyser: ${error.message}`, 'error');
        }
    }

    // Handle MCP messages
    handleMCPMessage(message) {
        const payload = message.payload || {};
        log(`Server sent: ${JSON.stringify(message)}`, 'info');

        if (payload.method === 'tools/list') {
            const tools = getMcpTools();

            const replyMessage = JSON.stringify({
                "session_id": message.session_id || "",
                "type": "mcp",
                "payload": {
                    "jsonrpc": "2.0",
                    "id": payload.id,
                    "result": {
                        "tools": tools
                    }
                }
            });
            log(`Client reported: ${replyMessage}`, 'info');
            this.websocket.send(replyMessage);
            log(`Replied with MCP tool list: ${tools.length} tools`, 'info');

        } else if (payload.method === 'tools/call') {
            const toolName = payload.params?.name;
            const toolArgs = payload.params?.arguments;

            log(`Calling tool: ${toolName} arguments: ${JSON.stringify(toolArgs)}`, 'info');

            executeMcpTool(toolName, toolArgs).then(result => {
                const replyMessage = JSON.stringify({
                    "session_id": message.session_id || "",
                    "type": "mcp",
                    "payload": {
                        "jsonrpc": "2.0",
                        "id": payload.id,
                        "result": {
                            "content": [
                                {
                                    "type": "text",
                                    "text": JSON.stringify(result)
                                }
                            ],
                            "isError": false
                        }
                    }
                });

                log(`Client reported: ${replyMessage}`, 'info');
                this.websocket.send(replyMessage);
            }).catch(error => {
                log(`Tool execution failed: ${error.message}`, 'error');
                const errorReply = JSON.stringify({
                    "session_id": message.session_id || "",
                    "type": "mcp",
                    "payload": {
                        "jsonrpc": "2.0",
                        "id": payload.id,
                        "error": {
                            "code": -32603,
                            "message": error.message
                        }
                    }
                });
                this.websocket.send(errorReply);
            });
        } else if (payload.method === 'initialize') {
            log(`Received tool initialize request: ${JSON.stringify(payload.params)}`, 'info');
            // Save the vision analysis endpoint URL
            const visionUrl = document.getElementById('visionUrl');
            const visionConfig = payload?.params?.capabilities?.vision;
            if (visionConfig && typeof visionConfig === 'object' && visionConfig.url && visionConfig.token) {
                const visionConfigStr = JSON.stringify(visionConfig);
                localStorage.setItem('xz_tester_vision', visionConfigStr);
                if (visionUrl) visionUrl.value = visionConfig.url;
            } else {
                localStorage.removeItem('xz_tester_vision');
                if (visionUrl) visionUrl.value = '';
            }

            const replyMessage = JSON.stringify({
                "session_id": message.session_id || "",
                "type": "mcp",
                "payload": {
                    "jsonrpc": "2.0",
                    "id": payload.id,
                    "result": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {
                            "tools": {}
                        },
                        "serverInfo": {
                            "name": "xiaozhi-web-test",
                            "version": "2.1.0"
                        }
                    }
                }
            });
            log(`Replied with initialize response`, 'info');
            this.websocket.send(replyMessage);
        } else {
            log(`Unknown MCP method: ${payload.method}`, 'warning');
        }
    }

    // Handle binary messages
    async handleBinaryMessage(data) {
        try {
            let arrayBuffer;
            if (data instanceof ArrayBuffer) {
                arrayBuffer = data;
            } else if (data instanceof Blob) {
                arrayBuffer = await data.arrayBuffer();
                log(`Received Blob audio data, size: ${arrayBuffer.byteLength} bytes`, 'debug');
            } else {
                log(`Received binary data of unknown type: ${typeof data}`, 'warning');
                return;
            }

            const opusData = new Uint8Array(arrayBuffer);
            const audioPlayer = getAudioPlayer();
            audioPlayer.enqueueAudioData(opusData);
        } catch (error) {
            log(`Error handling binary message: ${error.message}`, 'error');
        }
    }

    // Connect to the WebSocket server
    async connect() {
        const config = getConfig();
        log('Checking OTA status...', 'info');
        saveConnectionUrls();

        try {
            const otaUrl = document.getElementById('otaUrl').value.trim();
            const ws = await webSocketConnect(otaUrl, config);
            if (ws === undefined) {
                return false;
            }
            this.websocket = ws;

            // Receive binary data as ArrayBuffer
            this.websocket.binaryType = 'arraybuffer';

            // Set the WebSocket instance for the MCP module
            setMcpWebSocket(this.websocket);

            // Set the recorder's WebSocket
            const audioRecorder = getAudioRecorder();
            audioRecorder.setWebSocket(this.websocket);

            this.setupEventHandlers();

            return true;
        } catch (error) {
            log(`Connection error: ${error.message}`, 'error');
            if (this.onConnectionStateChange) {
                this.onConnectionStateChange(false);
            }
            return false;
        }
    }

    // Set up event handlers
    setupEventHandlers() {
        this.websocket.onopen = async () => {
            const url = document.getElementById('serverUrl').value;
            log(`Connected to server: ${url}`, 'success');

            if (this.onConnectionStateChange) {
                this.onConnectionStateChange(true);
            }

            // After connecting, the default state is listening
            this.isRemoteSpeaking = false;
            if (this.onSessionStateChange) {
                this.onSessionStateChange(false);
            }

            // Initialize the Live2D audio analyser once the WebSocket is connected
            this.initializeLive2DAudioAnalyzer();

            await this.sendHelloMessage();
        };

        this.websocket.onclose = () => {
            log('Disconnected', 'info');

            if (this.onConnectionStateChange) {
                this.onConnectionStateChange(false);
            }

            const audioRecorder = getAudioRecorder();
            audioRecorder.stop();

            // Turn off the camera
            if (typeof window.stopCamera === 'function') {
                window.stopCamera();
            }

            // Hide the camera display area
            const cameraContainer = document.getElementById('cameraContainer');
            if (cameraContainer) {
                cameraContainer.classList.remove('active');
            }
        };

        this.websocket.onerror = (error) => {
            log(`WebSocket error: ${error.message || 'Unknown error'}`, 'error');
            uiController.addChatMessage(`⚠️ WebSocket error: ${error.message || 'Unknown error'}`, false);
            if (this.onConnectionStateChange) {
                this.onConnectionStateChange(false);
            }
        };

        this.websocket.onmessage = (event) => {
            try {
                if (typeof event.data === 'string') {
                    const message = JSON.parse(event.data);
                    this.handleTextMessage(message);
                } else {
                    this.handleBinaryMessage(event.data);
                }
            } catch (error) {
                log(`WebSocket message handling error: ${error.message}`, 'error');
                // The old addMessage function is no longer used because the conversationDiv element does not exist
                // Error messages are shown by other means
            }
        };
    }

    // Disconnect
    disconnect() {
        if (!this.websocket) return;

        this.websocket.close();
        const audioRecorder = getAudioRecorder();
        audioRecorder.stop();

        // Turn off the camera
        if (typeof window.stopCamera === 'function') {
            window.stopCamera();
        }

        // Hide the camera display area
        const cameraContainer = document.getElementById('cameraContainer');
        if (cameraContainer) {
            cameraContainer.classList.remove('active');
        }
    }

    // Send a text message
    sendTextMessage(text) {
        if (text === '' || !this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
            return false;
        }

        try {
            // If the other side is speaking, send an abort message first
            if (this.isRemoteSpeaking && this.currentSessionId) {
                const abortMessage = {
                    session_id: this.currentSessionId,
                    type: 'abort',
                    reason: 'wake_word_detected'
                };
                this.websocket.send(JSON.stringify(abortMessage));
                log('Sent abort message', 'info');
            }

            const listenMessage = {
                type: 'listen',
                state: 'detect',
                text: text
            };

            this.websocket.send(JSON.stringify(listenMessage));
            log(`Sent text message: ${text}`, 'info');

            return true;
        } catch (error) {
            log(`Error sending message: ${error.message}`, 'error');
            return false;
        }
    }

    /**
     * Trigger a Live2D emotion motion
     * @param {string} emotion - Emotion name
     */
    triggerLive2DEmotionAction(emotion) {
        try {
            const live2dManager = window.chatApp?.live2dManager;
            if (live2dManager && typeof live2dManager.triggerEmotionAction === 'function') {
                live2dManager.triggerEmotionAction(emotion);
                log(`Triggered Live2D emotion motion: ${emotion}`, 'info');
            } else {
                log(`Cannot trigger Live2D emotion motion: Live2D manager not found or method unavailable`, 'warning');
            }
        } catch (error) {
            log(`Failed to trigger Live2D emotion motion: ${error.message}`, 'error');
        }
    }

    // Get the WebSocket instance
    getWebSocket() {
        return this.websocket;
    }

    // Check whether connected
    isConnected() {
        return this.websocket && this.websocket.readyState === WebSocket.OPEN;
    }
}

// Create the singleton
let wsHandlerInstance = null;

export function getWebSocketHandler() {
    if (!wsHandlerInstance) {
        wsHandlerInstance = new WebSocketHandler();
    }
    return wsHandlerInstance;
}
