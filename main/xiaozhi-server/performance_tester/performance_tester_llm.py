import asyncio
import logging
import os
import statistics
import time
import concurrent.futures
from typing import Dict, Optional
import yaml
import aiohttp
from tabulate import tabulate
from core.utils.llm import create_instance as create_llm_instance
from config.settings import load_config

# Set the global log level to WARNING to suppress INFO logs
logging.basicConfig(level=logging.WARNING)

description = "Large language model (LLM) performance test"


class LLMPerformanceTester:
    def __init__(self, config):
        self.config = config
        # Use test content closer to real agent scenarios, including the system prompt (test inputs kept in Chinese)
        self.system_prompt = self._load_system_prompt()
        self.test_sentences = self.config.get("module_test", {}).get(
            "test_sentences",
            [
                "你好，我今天心情不太好，能安慰一下我吗？",
                "帮我查一下明天的天气如何？",
                "我想听一个有趣的故事，你能给我讲一个吗？",
                "现在几点了？今天是星期几？",
                "我想设置一个明天早上8点的闹钟提醒我开会",
            ],
        )
        self.results = {}

    def _load_system_prompt(self) -> str:
        """Load the system prompt"""
        try:
            prompt_file = os.path.join(
                os.path.dirname(os.path.dirname(__file__)), self.config.get("prompt_template", "agent-base-prompt.txt")
            )
            with open(prompt_file, "r", encoding="utf-8") as f:
                content = f.read()
                # Replace template variables with test values
                content = content.replace(
                    "{{base_prompt}}", "你是小智，一个聪明可爱的AI助手"
                )
                content = content.replace(
                    "{{emojiList}}", "😀,😃,😄,😁,😊,😍,🤔,😮,😱,😢,😭,😴,😵,🤗,🙄"
                )
                content = content.replace("{{current_time}}", "2024年8月17日 12:30:45")
                content = content.replace("{{today_date}}", "2024年8月17日")
                content = content.replace("{{today_weekday}}", "星期六")
                content = content.replace("{{lunar_date}}", "甲辰年七月十四")
                content = content.replace("{{local_address}}", "北京市")
                content = content.replace("{{weather_info}}", "今天晴，25-32℃")
                return content
        except Exception as e:
            print(f"Unable to load the system prompt file: {e}")
            return "你是小智，一个聪明可爱的AI助手。请用温暖友善的语气回复用户。"

    def _collect_response_sync(self, llm, messages, llm_name, sentence_start):
        """Helper that collects response data synchronously"""
        chunks = []
        first_token_received = False
        first_token_time = None

        try:
            response_generator = llm.response("perf_test", messages)
            chunk_count = 0
            for chunk in response_generator:
                chunk_count += 1
                # Every few chunks, check whether we should stop
                if chunk_count % 10 == 0:
                    # Exit early if the current thread has been marked as interrupted
                    import threading

                    if (
                        threading.current_thread().ident
                        != threading.main_thread().ident
                    ):
                        # If not on the main thread, check whether to stop
                        pass

                # Check whether the chunk contains an error message (providers emit Chinese error text)
                chunk_str = str(chunk)
                if (
                    "异常" in chunk_str
                    or "错误" in chunk_str
                    or "502" in chunk_str.lower()
                ):
                    error_msg = chunk_str.lower()
                    print(f"{llm_name} response contains an error message: {error_msg}")
                    # Raise an exception carrying the error message
                    raise Exception(chunk_str)

                if not first_token_received and chunk.strip() != "":
                    first_token_time = time.time() - sentence_start
                    first_token_received = True
                    print(f"{llm_name} first token: {first_token_time:.3f}s")
                chunks.append(chunk)
        except Exception as e:
            # More detailed error info
            error_msg = str(e).lower()
            print(f"{llm_name} response collection exception: {error_msg}")
            # For 502 or network errors, re-raise so the caller handles it
            if (
                "502" in error_msg
                or "bad gateway" in error_msg
                or "error code: 502" in error_msg
                or "异常" in str(e)
                or "错误" in str(e)
            ):
                raise e
            # For other errors, return partial results
            return chunks, first_token_time

        return chunks, first_token_time

    async def _check_ollama_service(self, base_url: str, model_name: str) -> bool:
        """Asynchronously check the Ollama service status"""
        async with aiohttp.ClientSession() as session:
            try:
                async with session.get(f"{base_url}/api/version") as response:
                    if response.status != 200:
                        print(f"Ollama service is not running or unreachable: {base_url}")
                        return False
                async with session.get(f"{base_url}/api/tags") as response:
                    if response.status == 200:
                        data = await response.json()
                        models = data.get("models", [])
                        if not any(model["name"] == model_name for model in models):
                            print(
                                f"Ollama model {model_name} not found, please download it first with `ollama pull {model_name}`"
                            )
                            return False
                    else:
                        print("Unable to get the Ollama model list")
                        return False
                return True
            except Exception as e:
                print(f"Unable to connect to the Ollama service: {str(e)}")
                return False

    async def _test_single_sentence(
        self, llm_name: str, llm, sentence: str
    ) -> Optional[Dict]:
        """Test performance for a single sentence"""
        try:
            print(f"{llm_name} starting test: {sentence[:20]}...")
            sentence_start = time.time()
            first_token_received = False
            first_token_time = None

            # Build messages including the system prompt
            messages = [
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": sentence},
            ]

            # Use asyncio.wait_for for timeout control
            try:
                loop = asyncio.get_event_loop()
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    # Create the response collection task
                    future = executor.submit(
                        self._collect_response_sync,
                        llm,
                        messages,
                        llm_name,
                        sentence_start,
                    )

                    # Use asyncio.wait_for to implement the timeout
                    try:
                        response_chunks, first_token_time = await asyncio.wait_for(
                            asyncio.wrap_future(future), timeout=10.0
                        )
                    except asyncio.TimeoutError:
                        print(f"{llm_name} test timed out (10s), skipped")
                        # Force-cancel the future
                        future.cancel()
                        # Wait briefly so the thread pool task can respond to cancellation
                        try:
                            await asyncio.wait_for(
                                asyncio.wrap_future(future), timeout=1.0
                            )
                        except (
                            asyncio.TimeoutError,
                            concurrent.futures.CancelledError,
                            Exception,
                        ):
                            # Ignore all exceptions so the program keeps running
                            pass
                        return None

            except Exception as timeout_error:
                print(f"{llm_name} processing exception: {timeout_error}")
                return None

            response_time = time.time() - sentence_start
            print(f"{llm_name} response complete: {response_time:.3f}s")

            return {
                "name": llm_name,
                "type": "llm",
                "first_token_time": first_token_time,
                "response_time": response_time,
            }
        except Exception as e:
            error_msg = str(e).lower()
            # Check whether it is a 502 or network error
            if (
                "502" in error_msg
                or "bad gateway" in error_msg
                or "error code: 502" in error_msg
            ):
                print(f"{llm_name} got a 502 error, skipping test")
                return {
                    "name": llm_name,
                    "type": "llm",
                    "errors": 1,
                    "error_type": "502 network error",
                }
            print(f"{llm_name} sentence test failed: {str(e)}")
            return None

    async def _test_llm(self, llm_name: str, config: Dict) -> Dict:
        """Asynchronously test a single LLM"""
        try:
            # For Ollama, skip the api_key check and handle it specially
            if llm_name == "Ollama":
                base_url = config.get("base_url", "http://localhost:11434")
                model_name = config.get("model_name")
                if not model_name:
                    print("Ollama has no model_name configured")
                    return {
                        "name": llm_name,
                        "type": "llm",
                        "errors": 1,
                        "error_type": "Network error",
                    }

                if not await self._check_ollama_service(base_url, model_name):
                    return {
                        "name": llm_name,
                        "type": "llm",
                        "errors": 1,
                        "error_type": "Network error",
                    }
            else:
                if "api_key" in config and any(
                    x in config["api_key"] for x in ["你的", "placeholder", "sk-xxx"]
                ):
                    print(f"Skipping unconfigured LLM: {llm_name}")
                    return {
                        "name": llm_name,
                        "type": "llm",
                        "errors": 1,
                        "error_type": "Config error",
                    }

            # Get the actual type (compatible with old configs)
            module_type = config.get("type", llm_name)
            llm = create_llm_instance(module_type, config)

            # Always use UTF-8 encoding
            test_sentences = [
                s.encode("utf-8").decode("utf-8") for s in self.test_sentences
            ]

            # Create test tasks for all sentences
            sentence_tasks = []
            for sentence in test_sentences:
                sentence_tasks.append(
                    self._test_single_sentence(llm_name, llm, sentence)
                )

            # Run all sentence tests concurrently and handle exceptions
            sentence_results = await asyncio.gather(
                *sentence_tasks, return_exceptions=True
            )

            # Process results, filtering out exceptions and None values
            valid_results = []
            for result in sentence_results:
                if isinstance(result, dict) and result is not None:
                    valid_results.append(result)
                elif isinstance(result, Exception):
                    error_msg = str(result).lower()
                    if "502" in error_msg or "bad gateway" in error_msg:
                        print(f"{llm_name} got a 502 error, skipping this sentence test")
                        return {
                            "name": llm_name,
                            "type": "llm",
                            "errors": 1,
                            "error_type": "502 network error",
                        }
                    else:
                        print(f"{llm_name} sentence test exception: {result}")

            if not valid_results:
                print(f"{llm_name} has no valid data, possibly a network problem or config error")
                return {
                    "name": llm_name,
                    "type": "llm",
                    "errors": 1,
                    "error_type": "Network error",
                }

            # Check the number of valid results; too few means the test failed
            if len(valid_results) < len(test_sentences) * 0.3:  # Require at least a 30% success rate
                print(
                    f"{llm_name} too few successful sentences ({len(valid_results)}/{len(test_sentences)}), the network may be unstable or the API has issues"
                )
                return {
                    "name": llm_name,
                    "type": "llm",
                    "errors": 1,
                    "error_type": "Network error",
                }

            first_token_times = [
                r["first_token_time"]
                for r in valid_results
                if r.get("first_token_time")
            ]
            response_times = [r["response_time"] for r in valid_results]

            # Filter outliers (beyond 3 standard deviations)
            if len(response_times) > 1:
                mean = statistics.mean(response_times)
                stdev = statistics.stdev(response_times)
                filtered_times = [t for t in response_times if t <= mean + 3 * stdev]
            else:
                filtered_times = response_times

            return {
                "name": llm_name,
                "type": "llm",
                "avg_response": sum(response_times) / len(response_times),
                "avg_first_token": (
                    sum(first_token_times) / len(first_token_times)
                    if first_token_times
                    else 0
                ),
                "success_rate": f"{len(valid_results)}/{len(test_sentences)}",
                "errors": 0,
            }
        except Exception as e:
            error_msg = str(e).lower()
            if "502" in error_msg or "bad gateway" in error_msg:
                print(f"LLM {llm_name} got a 502 error, skipping test")
            else:
                print(f"LLM {llm_name} test failed: {str(e)}")
            error_type = "Network error"
            if "timeout" in str(e).lower():
                error_type = "Connection timeout"
            return {
                "name": llm_name,
                "type": "llm",
                "errors": 1,
                "error_type": error_type,
            }

    def _print_results(self):
        """Print test results"""
        print("\n" + "=" * 50)
        print("LLM performance test results")
        print("=" * 50)

        if not self.results:
            print("No test results available")
            return

        headers = ["Model", "Avg response time (s)", "First token time (s)", "Success rate", "Status"]
        table_data = []

        # Collect and classify all data
        valid_results = []
        error_results = []

        for name, data in self.results.items():
            if data["errors"] == 0:
                # Successful results
                avg_response = f"{data['avg_response']:.3f}"
                avg_first_token = (
                    f"{data['avg_first_token']:.3f}"
                    if data["avg_first_token"] > 0
                    else "-"
                )
                success_rate = data.get("success_rate", "N/A")
                status = "✅ OK"

                # Save the value used for sorting
                first_token_value = (
                    data["avg_first_token"]
                    if data["avg_first_token"] > 0
                    else float("inf")
                )

                valid_results.append(
                    {
                        "name": name,
                        "avg_response": avg_response,
                        "avg_first_token": avg_first_token,
                        "success_rate": success_rate,
                        "status": status,
                        "sort_key": first_token_value,
                    }
                )
            else:
                # Error results
                avg_response = "-"
                avg_first_token = "-"
                success_rate = "0/5"

                # Get the specific error type
                error_type = data.get("error_type", "Network error")
                status = f"❌ {error_type}"

                error_results.append(
                    [name, avg_response, avg_first_token, success_rate, status]
                )

        # Sort by first token time ascending
        valid_results.sort(key=lambda x: x["sort_key"])

        # Convert the sorted valid results into table rows
        for result in valid_results:
            table_data.append(
                [
                    result["name"],
                    result["avg_response"],
                    result["avg_first_token"],
                    result["success_rate"],
                    result["status"],
                ]
            )

        # Append error results to the end of the table
        table_data.extend(error_results)

        print(tabulate(table_data, headers=headers, tablefmt="grid"))
        print("\nTest notes:")
        print("- Test content: agent conversation scenarios with the full system prompt")
        print("- Timeout: max wait per request is 10 seconds")
        print("- Error handling: models with 502 errors or network exceptions are skipped automatically")
        print("- Success rate: successfully answered sentences / total test sentences")
        print("\nTest complete!")

    async def run(self):
        """Run the full async test"""
        print("Filtering available LLM modules...")

        # Create all test tasks
        all_tasks = []

        # LLM test tasks
        if self.config.get("LLM") is not None:
            for llm_name, config in self.config.get("LLM", {}).items():
                # Check config validity
                if llm_name == "CozeLLM":
                    if any(x in config.get("bot_id", "") for x in ["你的"]) or any(
                        x in config.get("user_id", "") for x in ["你的"]
                    ):
                        print(f"LLM {llm_name} has no bot_id/user_id configured, skipped")
                        continue
                elif "api_key" in config and any(
                    x in config["api_key"] for x in ["你的", "placeholder", "sk-xxx"]
                ):
                    print(f"LLM {llm_name} has no api_key configured, skipped")
                    continue

                # For Ollama, check the service status first
                if llm_name == "Ollama":
                    base_url = config.get("base_url", "http://localhost:11434")
                    model_name = config.get("model_name")
                    if not model_name:
                        print("Ollama has no model_name configured")
                        continue

                    if not await self._check_ollama_service(base_url, model_name):
                        continue

                print(f"Adding LLM test task: {llm_name}")
                all_tasks.append(self._test_llm(llm_name, config))

        print(f"\nFound {len(all_tasks)} available LLM modules")
        print("\nStarting concurrent tests of all modules...\n")

        # Run all test tasks concurrently, each with its own timeout
        async def test_with_timeout(task, timeout=30):
            """Add timeout protection to each test task"""
            try:
                return await asyncio.wait_for(task, timeout=timeout)
            except asyncio.TimeoutError:
                print(f"Test task timed out ({timeout}s), skipped")
                return {
                    "name": "Unknown",
                    "type": "llm",
                    "errors": 1,
                    "error_type": "Connection timeout",
                }
            except Exception as e:
                print(f"Test task exception: {str(e)}")
                return {
                    "name": "Unknown",
                    "type": "llm",
                    "errors": 1,
                    "error_type": "Network error",
                }

        # Wrap each task with timeout protection
        protected_tasks = [test_with_timeout(task) for task in all_tasks]

        # Run all test tasks concurrently
        all_results = await asyncio.gather(*protected_tasks, return_exceptions=True)

        # Process results
        for result in all_results:
            if isinstance(result, dict):
                if result.get("errors") == 0:
                    self.results[result["name"]] = result
                else:
                    # Record even on error so the failure status is shown
                    if result.get("name") != "Unknown":
                        self.results[result["name"]] = result
            elif isinstance(result, Exception):
                print(f"Exception while processing test result: {str(result)}")

        # Print results
        print("\nGenerating test report...")
        self._print_results()


async def main():
    config = await load_config()
    tester = LLMPerformanceTester(config)
    await tester.run()


if __name__ == "__main__":
    asyncio.run(main())
