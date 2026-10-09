import os
import re
import sys
import importlib

from config.logger import setup_logging
from core.utils.textUtils import check_emoji

logger = setup_logging()

punctuation_set = {
    "，",
    ",",  # Chinese comma + English comma
    "。",
    ".",  # Chinese period + English period
    "！",
    "!",  # Chinese exclamation mark + English exclamation mark
    "“",
    "”",
    '"',  # Chinese double quotes + English quote
    "：",
    ":",  # Chinese colon + English colon
    "-",
    "－",  # English hyphen + Chinese full-width dash
    "、",  # Chinese enumeration comma
    "[",
    "]",  # square brackets
    "【",
    "】",  # Chinese square brackets
    "~",  # tilde
}

def create_instance(class_name, *args, **kwargs):
    # Create the TTS instance
    if os.path.exists(os.path.join('core', 'providers', 'tts', f'{class_name}.py')):
        lib_name = f'core.providers.tts.{class_name}'
        if lib_name not in sys.modules:
            sys.modules[lib_name] = importlib.import_module(f'{lib_name}')
        return sys.modules[lib_name].TTSProvider(*args, **kwargs)

    raise ValueError(f"Unsupported TTS type: {class_name}, please check that the type in this config is set correctly")


class MarkdownCleaner:
    """
    Wraps the Markdown cleanup logic: just call MarkdownCleaner.clean_markdown(text)
    """
    # Formula characters
    NORMAL_FORMULA_CHARS = re.compile(r'[a-zA-Z\\^_{}\+\-\(\)\[\]=]')

    @staticmethod
    def _replace_inline_dollar(m: re.Match) -> str:
        """
        Whenever a complete "$...$" is captured:
          - if it contains typical formula characters => strip the surrounding $
          - otherwise (plain numbers/currency etc.) => keep "$...$"
        """
        content = m.group(1)
        if MarkdownCleaner.NORMAL_FORMULA_CHARS.search(content):
            return content
        else:
            return m.group(0)

    @staticmethod
    def _replace_table_block(match: re.Match) -> str:
        """
        Callback invoked when a whole table block is matched.
        """
        block_text = match.group('table_block')
        lines = block_text.strip('\n').split('\n')

        parsed_table = []
        for line in lines:
            line_stripped = line.strip()
            if re.match(r'^\|\s*[-:]+\s*(\|\s*[-:]+\s*)+\|?$', line_stripped):
                continue
            columns = [col.strip() for col in line_stripped.split('|') if col.strip() != '']
            if columns:
                parsed_table.append(columns)

        if not parsed_table:
            return ""

        headers = parsed_table[0]
        data_rows = parsed_table[1:] if len(parsed_table) > 1 else []

        lines_for_tts = []
        if len(parsed_table) == 1:
            # Only one row
            only_line_str = ", ".join(parsed_table[0])
            lines_for_tts.append(f"单行表格：{only_line_str}")
        else:
            lines_for_tts.append(f"表头是：{', '.join(headers)}")
            for i, row in enumerate(data_rows, start=1):
                row_str_list = []
                for col_index, cell_val in enumerate(row):
                    if col_index < len(headers):
                        row_str_list.append(f"{headers[col_index]} = {cell_val}")
                    else:
                        row_str_list.append(cell_val)
                lines_for_tts.append(f"第 {i} 行：{', '.join(row_str_list)}")

        return "\n".join(lines_for_tts) + "\n"

    # Precompile all regexes (ordered by execution frequency)
    # The replace_xxx static methods must be defined first so they can be referenced in the list.
    REGEXES = [
        (re.compile(r'```.*?```', re.DOTALL), ''),  # code block
        (re.compile(r'^#+\s*', re.MULTILINE), ''),  # heading
        (re.compile(r'(\*\*|__)(.*?)\1'), r'\2'),  # bold
        (re.compile(r'(\*|_)(?=\S)(.*?)(?<=\S)\1'), r'\2'),  # italic
        (re.compile(r'!\[.*?\]\(.*?\)'), ''),  # image
        (re.compile(r'\[(.*?)\]\(.*?\)'), r'\1'),  # link
        (re.compile(r'^\s*>+\s*', re.MULTILINE), ''),  # blockquote
        (
            re.compile(r'(?P<table_block>(?:^[^\n]*\|[^\n]*\n)+)', re.MULTILINE),
            _replace_table_block
        ),
        (re.compile(r'^\s*[*+-]\s*', re.MULTILINE), '- '),  # list
        (re.compile(r'\$\$.*?\$\$', re.DOTALL), ''),  # block formula
        (
            re.compile(r'(?<![A-Za-z0-9])\$([^\n$]+)\$(?![A-Za-z0-9])'),
            _replace_inline_dollar
        ),
        (re.compile(r'\n{2,}'), '\n'),  # extra blank lines
    ]

    @staticmethod
    def clean_markdown(text: str) -> str:
        """
        Main entry: apply all regexes in order to remove or replace Markdown elements
        """
        for regex, replacement in MarkdownCleaner.REGEXES:
            text = regex.sub(replacement, text)

        # Remove emoji
        text = check_emoji(text)

        # Check whether the text is all English and basic punctuation
        if text and all((c.isascii() or c.isspace() or c in punctuation_set) for c in text):
            # Keep original spaces and return directly
            return text

        return text.strip()

def convert_percentage_to_range(percentage, min_val, max_val, base_val=None):
    """
    Convert a percentage (-100~100) to a value in the target range

    Args:
        percentage: percentage (-100 to 100)
        min_val: target range minimum
        max_val: target range maximum
        base_val: base value (optional, defaults to the range midpoint)

    Returns:
        the converted value
    """
    percentage, min_val, max_val = float(percentage), float(min_val), float(max_val)
    base_val = float(base_val) if base_val is not None else (min_val + max_val) / 2

    if percentage < 0:
        # Negative percentage: interpolate linearly from base_val to min_val
        result = base_val + (base_val - min_val) * (percentage / 100)
    else:
        # Positive percentage: interpolate linearly from base_val to max_val
        result = base_val + (max_val - base_val) * (percentage / 100)

    # Clamp the result to the valid range
    return max(min_val, min(max_val, result))
