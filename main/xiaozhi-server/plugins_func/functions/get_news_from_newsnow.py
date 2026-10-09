import random
import httpx
from io import BytesIO
from markitdown import MarkItDown, StreamInfo
from config.logger import setup_logging
from plugins_func.register import register_function, ToolType, ActionResponse, Action
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler


TAG = __name__
logger = setup_logging()

CHANNEL_MAP = {
    "V2EX": "v2ex-share",
    "知乎": "zhihu",
    "微博": "weibo",
    "联合早报": "zaobao",
    "酷安": "coolapk",
    "MKTNews": "mktnews-flash",
    "华尔街见闻": "wallstreetcn-quick",
    "36氪": "36kr-quick",
    "抖音": "douyin",
    "虎扑": "hupu",
    "百度贴吧": "tieba",
    "今日头条": "toutiao",
    "IT之家": "ithome",
    "澎湃新闻": "thepaper",
    "卫星通讯社": "sputniknewscn",
    "参考消息": "cankaoxiaoxi",
    "远景论坛": "pcbeta-windows11",
    "财联社": "cls-depth",
    "雪球": "xueqiu-hotstock",
    "格隆汇": "gelonghui",
    "法布财经": "fastbull-express",
    "Solidot": "solidot",
    "Hacker News": "hackernews",
    "Product Hunt": "producthunt",
    "Github": "github-trending-today",
    "哔哩哔哩": "bilibili-hot-search",
    "快手": "kuaishou",
    "靠谱新闻": "kaopu",
    "金十数据": "jin10",
    "百度热搜": "baidu",
    "牛客": "nowcoder",
    "少数派": "sspai",
    "稀土掘金": "juejin",
    "凤凰网": "ifeng",
    "虫部落": "chongbuluo-latest",
}

# Default news sources, used when none are specified in config
DEFAULT_NEWS_SOURCES = "澎湃新闻;百度热搜;财联社"

def _get_newsnow_config(conn):
    # Read from the connection config
    plugins = conn.config.get("plugins", {})
    newsnow = plugins.get("get_news_from_newsnow", {})
    sources = newsnow.get("news_sources", "")
    if isinstance(sources, str) and sources.strip():
        return sources

    return ""

def get_news_sources_from_config(conn):
    """Get the news source string from config"""
    try:
        result = _get_newsnow_config(conn)
        if result:
            logger.bind(tag=TAG).debug(f"Using configured news sources: {result}")
            return result

        logger.bind(tag=TAG).debug("No news source config found, using defaults")
        return DEFAULT_NEWS_SOURCES

    except Exception as e:
        logger.bind(tag=TAG).error(f"Failed to get news source config: {e}, using defaults")
        return DEFAULT_NEWS_SOURCES


# Available news source names from the default config (resolved dynamically at runtime by get_news_sources_from_config)
example_sources_str = DEFAULT_NEWS_SOURCES.replace(";","、")

GET_NEWS_FROM_NEWSNOW_FUNCTION_DESC = {
    "type": "function",
    "function": {
        "name": "get_news_from_newsnow",
        "description": "当用户要求查看或收听新闻时调用（如'来条新闻''今天有什么新闻'）。",
        "parameters": {
            "type": "object",
            "properties": {
                "source": {
                    "type": "string",
                    "description": f"新闻源的标准中文名称，例如{example_sources_str}等。可选参数，如果不提供则使用默认新闻源",
                },
                "detail": {
                    "type": "boolean",
                    "description": "是否获取详细内容，默认为false。如果为true，则获取上一条新闻的详细内容",
                },
                "lang": {
                    "type": "string",
                    "description": "返回用户使用的语言code，例如zh_CN/zh_HK/en_US/ja_JP等，默认zh_CN",
                },
            },
            "required": ["lang"],
        },
    },
}


async def fetch_news_from_api(conn: "ConnectionHandler", source="thepaper"):
    """Fetch the news list from the API"""
    try:
        api_url = f"https://newsnow.busiyi.world/api/s?id={source}"

        news_config = conn.config.get("plugins", {}).get("get_news_from_newsnow", {})
        if news_config.get("url"):
            api_url = news_config["url"] + source

        headers = {"User-Agent": "Mozilla/5.0"}
        async with httpx.AsyncClient(timeout=httpx.Timeout(10.0, connect=3.0)) as client:
            response = await client.get(api_url, headers=headers)

        data = response.json()

        if "items" in data:
            return data["items"]
        else:
            logger.bind(tag=TAG).error(f"News API response has an unexpected format: {data}")
            return []

    except Exception as e:
        logger.bind(tag=TAG).error(f"News API request failed: {e}")
        return []


async def fetch_news_detail(url):
    """Fetch the news detail page and clean the HTML with MarkItDown"""
    try:
        headers = {"User-Agent": "Mozilla/5.0"}
        async with httpx.AsyncClient(timeout=httpx.Timeout(10.0, connect=3.0)) as client:
            response = await client.get(url, headers=headers)

        # Clean the HTML content with MarkItDown
        md = MarkItDown(enable_plugins=False)
        result = md.convert_stream(
            BytesIO(response.content),
            stream_info=StreamInfo(
                mimetype="text/html",
                extension=".html",
                charset=response.encoding or "utf-8",
            ),
        )

        # Get the cleaned text content
        clean_text = result.text_content

        # If the cleaned content is empty, return a notice
        if not clean_text or len(clean_text.strip()) == 0:
            logger.bind(tag=TAG).warning(f"News content is empty after cleaning: {url}")
            return "无法解析新闻详情内容，可能是网站结构特殊或内容受限。"

        return clean_text
    except Exception as e:
        logger.bind(tag=TAG).error(f"Failed to fetch news detail: {e}")
        return "无法获取详细内容"


@register_function(
    "get_news_from_newsnow",
    GET_NEWS_FROM_NEWSNOW_FUNCTION_DESC,
    ToolType.SYSTEM_CTL,
)
async def get_news_from_newsnow(
    conn: "ConnectionHandler",
    source: str = "澎湃新闻",
    detail: bool = False,
    lang: str = "zh_CN",
):
    """Fetch news and pick one at random to read out, or fetch the details of the previous news item"""
    try:
        # Get the currently configured news sources
        news_sources = get_news_sources_from_config(conn)

        # If detail is True, fetch the details of the previous news item
        detail = str(detail).lower() == "true"
        if detail:
            if (
                not hasattr(conn, "last_newsnow_link")
                or not conn.last_newsnow_link
                or "url" not in conn.last_newsnow_link
            ):
                return ActionResponse(
                    Action.REQLLM,
                    "抱歉，没有找到最近查询的新闻，请先获取一条新闻。",
                    None,
                )

            url = conn.last_newsnow_link.get("url")
            title = conn.last_newsnow_link.get("title", "未知标题")
            source_id = conn.last_newsnow_link.get("source_id", "thepaper")
            source_name = CHANNEL_MAP.get(source_id, "未知来源")

            if not url or url == "#":
                return ActionResponse(
                    Action.REQLLM, "抱歉，该新闻没有可用的链接获取详细内容。", None
                )

            logger.bind(tag=TAG).debug(
                f"Fetching news detail: {title}, source: {source_name}, URL={url}"
            )

            # Fetch the news detail
            detail_content = await fetch_news_detail(url)

            if not detail_content or detail_content == "无法获取详细内容":
                return ActionResponse(
                    Action.REQLLM,
                    f"抱歉，无法获取《{title}》的详细内容，可能是链接已失效或网站结构发生变化。",
                    None,
                )

            # Build the detail report
            detail_report = (
                f"根据下列数据，用{lang}回应用户的新闻详情查询请求：\n\n"
                f"新闻标题: {title}\n"
                # f"新闻来源: {source_name}\n"
                f"详细内容: {detail_content}\n\n"
                f"(请对上述新闻内容进行总结，提取关键信息，以自然、流畅的方式向用户播报，"
                f"不要提及这是总结，就像是在讲述一个完整的新闻)"
            )

            return ActionResponse(Action.REQLLM, detail_report, None)

        # Otherwise, fetch the news list and pick one at random
        # Convert the Chinese name into the English ID
        english_source_id = None

        # Check whether the given Chinese name is among the configured news sources
        news_sources_list = [
            name.strip() for name in news_sources.split(";") if name.strip()
        ]
        if source in news_sources_list:
            # If so, look up the corresponding English ID in CHANNEL_MAP
            english_source_id = CHANNEL_MAP.get(source)

        # If no English ID is found, use the default source
        if not english_source_id:
            logger.bind(tag=TAG).warning(f"Invalid news source: {source}, falling back to default source 澎湃新闻 (The Paper)")
            english_source_id = "thepaper"
            source = "澎湃新闻"

        logger.bind(tag=TAG).info(f"Fetching news: source={source}({english_source_id})")

        # Fetch the news list
        news_items = await fetch_news_from_api(conn, english_source_id)

        if not news_items:
            return ActionResponse(
                Action.REQLLM,
                f"抱歉，未能从{source}获取到新闻信息，请稍后再试或尝试其他新闻源。",
                None,
            )

        # Pick a random news item
        selected_news = random.choice(news_items)

        # Save the current news link on the connection for later detail queries
        if not hasattr(conn, "last_newsnow_link"):
            conn.last_newsnow_link = {}
        conn.last_newsnow_link = {
            "url": selected_news.get("url", "#"),
            "title": selected_news.get("title", "未知标题"),
            "source_id": english_source_id,
        }

        # Build the news report
        news_report = (
            f"根据下列数据，用{lang}回应用户的新闻查询请求：\n\n"
            f"新闻标题: {selected_news['title']}\n"
            # f"新闻来源: {source}\n"
            f"(请以自然、流畅的方式向用户播报这条新闻标题，"
            f"提示用户可以要求获取详细内容，此时会获取新闻的详细内容。)"
        )

        return ActionResponse(Action.REQLLM, news_report, None)

    except Exception as e:
        logger.bind(tag=TAG).error(f"Error while fetching news: {e}")
        return ActionResponse(
            Action.REQLLM, "抱歉，获取新闻时发生错误，请稍后再试。", None
        )
