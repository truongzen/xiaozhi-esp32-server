import json
import httpx
from config.logger import setup_logging
from plugins_func.register import register_function, ToolType, ActionResponse, Action
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

TAG = __name__
logger = setup_logging()

# Base function description template
SEARCH_FROM_RAGFLOW_FUNCTION_DESC = {
    "type": "function",
    "function": {
        "name": "search_from_ragflow",
        "description": "从知识库中查询信息",
        "parameters": {
            "type": "object",
            "properties": {"question": {"type": "string", "description": "查询的问题"}},
            "required": ["question"],
        },
    },
}


@register_function(
    "search_from_ragflow", SEARCH_FROM_RAGFLOW_FUNCTION_DESC, ToolType.SYSTEM_CTL
)
async def search_from_ragflow(conn: "ConnectionHandler", question=None):
    # Make sure string arguments are encoded correctly
    if question and isinstance(question, str):
        # Make sure the question argument is a UTF-8 string
        pass
    else:
        question = str(question) if question is not None else ""

    ragflow_config = conn.config.get("plugins", {}).get("search_from_ragflow", {})
    base_url = ragflow_config.get("base_url", "")
    api_key = ragflow_config.get("api_key", "")
    dataset_ids = ragflow_config.get("dataset_ids", [])

    url = base_url + "/api/v1/retrieval"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}

    # Make sure strings in the payload are UTF-8
    payload = {"question": question, "dataset_ids": dataset_ids}

    try:
        # Make sure Chinese is handled correctly during JSON serialization (ensure_ascii=False)
        async with httpx.AsyncClient(timeout=httpx.Timeout(5.0, connect=3.0), verify=False) as client:
            response = await client.post(url, json=payload, headers=headers)

        # Explicitly set the response encoding to utf-8
        response.encoding = "utf-8"

        response.raise_for_status()

        # Get the text content first, then decode the JSON manually
        response_text = response.text

        result = json.loads(response_text)

        if result.get("code") != 0:
            error_detail = result.get("error", {}).get("detail", "未知错误")
            error_message = result.get("error", {}).get("message", "")
            error_code = result.get("code", "")

            # Log the error safely
            logger.bind(tag=TAG).error(
                f"RAGFlow API call failed, code: {error_code}, error detail: {error_detail}, full response: {result}"
            )

            # Build a detailed error response (spoken to the user, kept in Chinese)
            error_response = f"RAG接口返回异常（错误码：{error_code}）"

            if error_message:
                error_response += f"：{error_message}"
            if error_detail:
                error_response += f"\n详情：{error_detail}"

            return ActionResponse(Action.RESPONSE, None, error_response)

        chunks = result.get("data", {}).get("chunks", [])
        contents = []
        for chunk in chunks:
            content = chunk.get("content", "")
            if content:
                # Handle the content string safely
                if isinstance(content, str):
                    contents.append(content)
                elif isinstance(content, bytes):
                    contents.append(content.decode("utf-8", errors="replace"))
                else:
                    contents.append(str(content))

        if contents:
            # Format knowledge base content as quoted context
            context_text = f"# 关于问题【{question}】查到知识库如下\n"
            context_text += "```\n\n\n".join(contents[:5])
            context_text += "\n```"
        else:
            context_text = "根据知识库查询结果，没有相关信息。"
        return ActionResponse(Action.REQLLM, context_text, None)

    except httpx.TimeoutException as e:
        error_response = "RAG接口请求超时"
        error_response += "\n可能原因：RAGflow服务响应缓慢或网络延迟"
        error_response += "\n解决方案：请稍后重试或检查RAGflow服务性能"
        return ActionResponse(Action.RESPONSE, None, error_response)

    except httpx.HTTPStatusError as e:
        if hasattr(e.response, "status_code"):
            status_code = e.response.status_code
            error_response = f"RAG接口HTTP错误（状态码：{status_code}）"
            try:
                error_detail = e.response.json().get("error", {}).get("message", "")
                if error_detail:
                    error_response += f"\n错误详情：{error_detail}"
            except:
                pass
        else:
            error_response = f"RAG接口HTTP异常：{str(e)}"
        return ActionResponse(Action.RESPONSE, None, error_response)

    except httpx.HTTPError as e:
        error_response = "无法连接到RAG接口"
        error_response += "\n可能原因：RAGflow服务地址错误或服务未运行"
        error_response += "\n解决方案：请检查RAGflow服务地址配置和服务状态"
        return ActionResponse(Action.RESPONSE, None, error_response)

    except Exception as e:
        # Other exceptions
        error_type = type(e).__name__
        logger.bind(tag=TAG).error(
            f"RAGflow processing exception, type: {error_type}, detail: {str(e)}"
        )

        # Provide detailed error info
        error_response = f"RAG接口处理异常（{error_type}）：{str(e)}"
        return ActionResponse(Action.RESPONSE, None, error_response)
