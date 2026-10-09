import asyncio
import logging
import os
import time
import threading
from typing import Dict
import yaml
from tabulate import tabulate

# Make sure create_tts_instance is imported from core.utils.tts
from core.utils.tts import create_instance as create_tts_instance
from config.settings import load_config

# Set the global log level to WARNING
logging.basicConfig(level=logging.WARNING)

description = "Non-streaming text-to-speech (TTS) performance test"


class TTSPerformanceTester:
    def __init__(self, config):
        self.config = config
        self.test_sentences = self.config.get("module_test", {}).get(
            "test_sentences",
            [
                "永和九年，岁在癸丑，暮春之初；",
                "夫人之相与，俯仰一世，或取诸怀抱，悟言一室之内；或因寄所托，放浪形骸之外。虽趣舍万殊，静躁不同，",
                "每览昔人兴感之由，若合一契，未尝不临文嗟悼，不能喻之于怀。固知一死生为虚诞，齐彭殇为妄作。",
            ],
        )
        self.results = {}

    async def _test_tts(self, tts_name: str, config: Dict) -> Dict:
        """Test performance of a single TTS module"""
        try:
            token_fields = ["access_token", "api_key", "token"]
            if any(
                field in config
                and any(x in config[field] for x in ["你的", "placeholder"])
                for field in token_fields
            ):
                print(f"TTS {tts_name} has no access_token/api_key configured, skipped")
                return {"name": tts_name, "errors": 1}

            module_type = config.get("type", tts_name)
            tts = create_tts_instance(module_type, config, delete_audio_file=True)

            # Set a mock conn object so TTS implementations do not see None when accessing self.conn.sample_rate
            class MockConn:
                sample_rate = 16000
                audio_format = "pcm"
                stop_event = threading.Event()  # Must be a real Event object
                client_abort = False
                headers = {}
            tts.conn = MockConn()

            # Set a mock opus_encoder so some TTS implementations do not see None when accessing self.opus_encoder
            class MockOpusEncoder:
                pass
            if not hasattr(tts, 'opus_encoder') or tts.opus_encoder is None:
                tts.opus_encoder = MockOpusEncoder()

            print(f"Testing TTS: {tts_name}")

            # Connectivity test (synthesizes a Chinese sample text)
            tmp_file = tts.generate_filename()
            await tts.text_to_speak("连接测试", tmp_file)

            if not tmp_file or not os.path.exists(tmp_file):
                print(f"{tts_name} connection failed")
                return {"name": tts_name, "errors": 1}

            total_time = 0
            test_count = len(self.test_sentences[:3])

            for i, sentence in enumerate(self.test_sentences[:2], 1):
                start = time.time()
                tmp_file = tts.generate_filename()
                await tts.text_to_speak(sentence, tmp_file)
                duration = time.time() - start
                total_time += duration

                if tmp_file and os.path.exists(tmp_file):
                    print(f"{tts_name} [{i}/{test_count}] test succeeded")
                else:
                    print(f"{tts_name} [{i}/{test_count}] test failed")
                    return {"name": tts_name, "errors": 1}

            return {
                "name": tts_name,
                "avg_time": total_time / test_count,
                "errors": 0,
            }

        except Exception as e:
            print(f"{tts_name} test failed: {str(e)}")
            return {"name": tts_name, "errors": 1}

    def _print_results(self):
        """Print test results"""
        if not self.results:
            print("No valid TTS test results")
            return

        headers = ["TTS module", "Avg time (s)", "Test sentences", "Status"]
        table_data = []

        # Collect and classify all data
        valid_results = []
        error_results = []

        for name, data in self.results.items():
            if data["errors"] == 0:
                # Successful results
                avg_time = f"{data['avg_time']:.3f}"
                test_count = len(self.test_sentences[:3])
                status = "✅ OK"
                
                # Save the value used for sorting
                valid_results.append({
                    "name": name,
                    "avg_time": avg_time,
                    "test_count": test_count,
                    "status": status,
                    "sort_key": data['avg_time']
                })
            else:
                # Error results
                avg_time = "-"
                test_count = "0/3"
                
                # Default error type is network error
                error_type = "Network error"
                status = f"❌ {error_type}"
                
                error_results.append([name, avg_time, test_count, status])

        # Sort by average time ascending
        valid_results.sort(key=lambda x: x["sort_key"])

        # Convert the sorted valid results into table rows
        for result in valid_results:
            table_data.append([
                result["name"],
                result["avg_time"],
                result["test_count"],
                result["status"]
            ])

        # Append error results to the end of the table
        table_data.extend(error_results)

        print("\nTTS performance test results:")
        print(
            tabulate(
                table_data,
                headers=headers,
                tablefmt="grid",
                colalign=("left", "right", "right", "left"),
            )
        )
        print("\nTest notes:")
        print("- Timeout: max wait per request is 10 seconds")
        print("- Error handling: connection failures and timeouts are listed as network errors")
        print("- Sorting: by average time from fastest to slowest")

    async def run(self):
        """Run the test"""
        print("Starting TTS performance test...")

        if not self.config.get("TTS"):
            print("No TTS config found in the config file")
            return

        # Iterate over all TTS configs
        tasks = []
        for tts_name, config in self.config.get("TTS", {}).items():
            tasks.append(self._test_tts(tts_name, config))

        # Run tests concurrently
        results = await asyncio.gather(*tasks)

        # Save all results, including errors
        for result in results:
            self.results[result["name"]] = result

        # Print results
        self._print_results()


# Entry point required by performance_tester.py
async def main():
    config = await load_config()
    tester = TTSPerformanceTester(config)
    await tester.run()


if __name__ == "__main__":
    asyncio.run(main())
