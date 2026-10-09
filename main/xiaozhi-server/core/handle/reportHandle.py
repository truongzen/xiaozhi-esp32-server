"""
TTS reporting is integrated into the ConnectionHandler class.

Reporting features:
1. Each connection object has its own report queue and processing thread
2. The report thread's lifecycle is bound to the connection object
3. Reports are made via the ConnectionHandler.enqueue_tts_report method

See the related code in core/connection.py for the implementation.
"""

import time
import json
import opuslib_next
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

from config.manage_api_client import report as manage_report

TAG = __name__


async def report(conn: "ConnectionHandler", chat_type, text, audio_data, report_time):
    """Report chat history

    Args:
        conn: connection object
        chat_type: report type; 1 = user (ASR/PCM), 2 = agent (TTS/Opus), 3 = tool call
        text: synthesized text
        audio_data: audio data (PCM when chat_type=1, Opus when chat_type=2)
        report_time: report time
    """
    try:
        if audio_data:

            if chat_type == 1:
                wav_data = pcm_to_wav(conn, audio_data)
            elif chat_type == 2:
                wav_data = opus_to_wav(conn, audio_data)
            else:
                wav_data = None
        else:
            wav_data = None
        # Perform the async report
        await manage_report(
            mac_address=conn.device_id,
            session_id=conn.session_id,
            chat_type=chat_type,
            content=text,
            audio=wav_data,
            report_time=report_time,
        )
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"Failed to report chat history: {e}")


def pcm_to_wav(conn: "ConnectionHandler", pcm_data):
    """Convert PCM data to WAV bytes

    Args:
        conn: connection object
        pcm_data: PCM audio data (a list or bytes)

    Returns:
        bytes: audio data in WAV format
    """
    try:
        # Handle PCM data that may be a list or bytes
        if isinstance(pcm_data, list):
            pcm_data_bytes = b"".join(pcm_data)
        else:
            pcm_data_bytes = pcm_data

        if not pcm_data_bytes:
            raise ValueError("No valid PCM data")

        # Create the WAV file header
        num_samples = len(pcm_data_bytes) // 2  # 16-bit samples

        # WAV file header
        wav_header = bytearray()
        wav_header.extend(b"RIFF")  # ChunkID
        wav_header.extend((36 + len(pcm_data_bytes)).to_bytes(4, "little"))  # ChunkSize
        wav_header.extend(b"WAVE")  # Format
        wav_header.extend(b"fmt ")  # Subchunk1ID
        wav_header.extend((16).to_bytes(4, "little"))  # Subchunk1Size
        wav_header.extend((1).to_bytes(2, "little"))  # AudioFormat (PCM)
        wav_header.extend((1).to_bytes(2, "little"))  # NumChannels
        wav_header.extend((16000).to_bytes(4, "little"))  # SampleRate
        wav_header.extend((32000).to_bytes(4, "little"))  # ByteRate
        wav_header.extend((2).to_bytes(2, "little"))  # BlockAlign
        wav_header.extend((16).to_bytes(2, "little"))  # BitsPerSample
        wav_header.extend(b"data")  # Subchunk2ID
        wav_header.extend(len(pcm_data_bytes).to_bytes(4, "little"))  # Subchunk2Size

        # Return the complete WAV data
        return bytes(wav_header) + pcm_data_bytes
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"PCM to WAV conversion failed: {e}", exc_info=True)
        raise


def opus_to_wav(conn: "ConnectionHandler", opus_data):
    """Convert Opus data to WAV bytes

    Args:
        conn: connection object
        opus_data: Opus audio data (a list or bytes)

    Returns:
        bytes: audio data in WAV format
    """
    decoder = None
    try:
        decoder = opuslib_next.Decoder(16000, 1)
        pcm_data = []

        if isinstance(opus_data, list):
            for opus_packet in opus_data:
                try:
                    pcm_frame = decoder.decode(opus_packet, 960)
                    pcm_data.append(pcm_frame)
                except opuslib_next.OpusError as e:
                    conn.logger.bind(tag=TAG).error(f"Opus decoding error: {e}", exc_info=True)
        elif isinstance(opus_data, bytes):
            pcm_frame = decoder.decode(opus_data, 960)
            pcm_data.append(pcm_frame)

        if not pcm_data:
            raise ValueError("No valid audio data")

        pcm_data_bytes = b"".join(pcm_data)

        wav_header = bytearray()
        wav_header.extend(b"RIFF")
        wav_header.extend((36 + len(pcm_data_bytes)).to_bytes(4, "little"))
        wav_header.extend(b"WAVE")
        wav_header.extend(b"fmt ")
        wav_header.extend((16).to_bytes(4, "little"))
        wav_header.extend((1).to_bytes(2, "little"))
        wav_header.extend((1).to_bytes(2, "little"))
        wav_header.extend((16000).to_bytes(4, "little"))
        wav_header.extend((32000).to_bytes(4, "little"))
        wav_header.extend((2).to_bytes(2, "little"))
        wav_header.extend((16).to_bytes(2, "little"))
        wav_header.extend(b"data")
        wav_header.extend(len(pcm_data_bytes).to_bytes(4, "little"))

        return bytes(wav_header) + pcm_data_bytes
    finally:
        if decoder is not None:
            try:
                del decoder
            except Exception as e:
                conn.logger.bind(tag=TAG).debug(f"Error while releasing decoder resources: {e}")


def enqueue_tts_report(conn: "ConnectionHandler", text, opus_data):
    """Add TTS data to the report queue

    Args:
        conn: connection object
        text: synthesized text
        opus_data: Opus audio data
    """
    if not conn.read_config_from_api or conn.need_bind or not conn.report_tts_enable:
        return
    if conn.chat_history_conf == 0:
        return
    try:
        # Use the connection's queue, passing text and binary data rather than a file path
        if conn.chat_history_conf == 2:
            conn.report_queue.put((2, text, opus_data, int(time.time() * 1000)))
            conn.logger.bind(tag=TAG).debug(
                f"TTS data added to report queue: {conn.device_id}, audio size: {len(opus_data)} "
            )
        else:
            conn.report_queue.put((2, text, None, int(time.time() * 1000)))
            conn.logger.bind(tag=TAG).debug(
                f"TTS data added to report queue: {conn.device_id}, audio not reported"
            )
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"Failed to add to TTS report queue: {text}, {e}")


def enqueue_tool_report(conn: "ConnectionHandler", tool_name: str, tool_input: dict, tool_result: str = None, report_tool_call: bool = True):
    """Add tool call data to the report queue

    Args:
        conn: connection object
        tool_name: tool name
        tool_input: tool input parameters
        tool_result: tool execution result (optional)
        report_tool_call: whether to report the tool call itself, default True; set to False to report only the result
    """
    if not conn.read_config_from_api or conn.need_bind:
        return
    if conn.chat_history_conf == 0:
        return

    try:
        timestamp = int(time.time() * 1000)

        # Build the tool call content
        if report_tool_call:
            tool_text = json.dumps(
                [
                    {
                        "type": "tool",
                        "text": f"{tool_name}({json.dumps(tool_input, ensure_ascii=False)})",
                    }
                ]
            )
            conn.report_queue.put((3, tool_text, None, timestamp))

        # Build the tool result content
        if tool_result:
            result_display = f'{{"result":"{str(tool_result)}"}}'
            result_content = json.dumps([{"type": "tool_result", "text": result_display}], ensure_ascii=False)
            conn.report_queue.put((3, result_content, None, timestamp + 1))
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"Failed to add to tool report queue: {e}")


def enqueue_asr_report(conn: "ConnectionHandler", text, opus_data):
    """Add ASR data to the report queue

    Args:
        conn: connection object
        text: synthesized text
        opus_data: Opus audio data
    """
    if not conn.read_config_from_api or conn.need_bind or not conn.report_asr_enable:
        return
    if conn.chat_history_conf == 0:
        return
    try:
        # Use the connection's queue, passing text and binary data rather than a file path
        if conn.chat_history_conf == 2:
            conn.report_queue.put((1, text, opus_data, int(time.time() * 1000)))
            conn.logger.bind(tag=TAG).debug(
                f"ASR data added to report queue: {conn.device_id}, audio size: {len(opus_data)} "
            )
        else:
            conn.report_queue.put((1, text, None, int(time.time() * 1000)))
            conn.logger.bind(tag=TAG).debug(
                f"ASR data added to report queue: {conn.device_id}, audio not reported"
            )
    except Exception as e:
        conn.logger.bind(tag=TAG).debug(f"Failed to add to ASR report queue: {text}, {e}")
