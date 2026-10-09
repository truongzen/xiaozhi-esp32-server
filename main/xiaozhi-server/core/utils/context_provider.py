import httpx
from typing import Dict, Any, List
from config.logger import setup_logging

TAG = __name__

class ContextDataProvider:
    """Context data filler that fetches data from configured APIs"""
    
    def __init__(self, config: Dict[str, Any], logger=None):
        self.config = config
        self.logger = logger or setup_logging()
        self.context_data = ""

    def fetch_all(self, device_id: str) -> str:
        """Fetch all configured context data"""
        context_providers = self.config.get("context_providers", [])
        if not context_providers:
            return ""

        formatted_lines = []
        for provider in context_providers:
            url = provider.get("url")
            headers = provider.get("headers", {})

            if not url:
                continue

            try:
                headers = headers.copy() if isinstance(headers, dict) else {}
                # Add device_id to the request headers
                headers["device-id"] = device_id
                
                # Send the request
                response = httpx.get(url, headers=headers, timeout=3)
                
                if response.status_code == 200:
                    result = response.json()
                    if isinstance(result, dict):
                        if result.get("code") == 0:
                            data = result.get("data")
                            # Format the data
                            if isinstance(data, dict):
                                for k, v in data.items():
                                    formatted_lines.append(f"- **{k}：** {v}")
                            elif isinstance(data, list):
                                for item in data:
                                    formatted_lines.append(f"- {item}")
                            else:
                                formatted_lines.append(f"- {data}")
                        else:
                            self.logger.bind(tag=TAG).warning(f"API {url} returned an error: {result.get('msg')}")
                    else:
                        self.logger.bind(tag=TAG).warning(f"API {url} did not return a JSON dict")
                else:
                    self.logger.bind(tag=TAG).warning(f"API {url} request failed: {response.status_code}")
            except Exception as e:
                self.logger.bind(tag=TAG).error(f"Failed to fetch context data {url}: {e}")
        
        # Join all formatted lines into a single string
        self.context_data = "\n".join(formatted_lines)
        if self.context_data:
            self.logger.bind(tag=TAG).debug(f"Injected dynamic context data:\n{self.context_data}")
        return self.context_data
