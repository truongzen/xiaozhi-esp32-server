# Voice Box Theme Customization

## Overview

This directory contains the static files built from the [xiaozhi-assets-generator](https://github.com/xinnan-tech/xiaozhi-assets-generator) project, used to customize and generate voice box themes online. With this tool users can configure the wake word, font, emoji and chat background, and export them as an `assets.bin` file.

## Directory structure

```
generator/
├── assets/              # Build output
│   ├── ft_render-ByO_jG18.js
│   ├── index-CYcyz9xb.js
│   └── index-NXxBVrod.css
├── static/              # Static resources
│   ├── charsets/        # Character set files
│   │   ├── deepseek.txt
│   │   ├── gb2312.txt
│   │   ├── latin1.txt
│   │   └── qwen18409.txt
│   ├── fonts/           # Font resources
│   │   ├── font_noto_qwen_14_1.bin
│   │   ├── font_noto_qwen_16_4.bin
│   │   ├── font_noto_qwen_20_4.bin
│   │   ├── font_noto_qwen_30_4.bin
│   │   ├── font_puhui_deepseek_14_1.bin
│   │   ├── font_puhui_deepseek_16_4.bin
│   │   ├── font_puhui_deepseek_20_4.bin
│   │   ├── font_puhui_deepseek_30_4.bin
│   │   ├── noto_qwen.ttf
│   │   └── puhui_deepseek.ttf
│   ├── multinet_model/  # Custom wake word models
│   │   ├── fst/
│   │   ├── mn6_cn/
│   │   ├── mn6_en/
│   │   ├── mn7_cn/
│   │   └── mn7_en/
│   ├── twemoji32/       # 32x32 emoji images
│   ├── twemoji64/       # 64x64 emoji images
│   ├── wakenet_model/   # Preset wake word models
│   └── README.md        # Static resources notes
├── index.html           # Main page
└── README.md            # This document
```

## Features

### 1. Chip and screen configuration
- Supports multiple chip models: ESP32-S3, ESP32-C3, ESP32-P4, ESP32-C6
- Flexible screen resolution settings
- Supports the RGB565 color format

### 2. Wake word configuration
- **Preset wake words**: based on the WakeNet models supported by each chip
- **Custom wake words**: Chinese and English command words, with configurable threshold and timeout

### 3. Font configuration
- Several preset fonts: Alibaba PuHuiTi, Noto Qwen, etc.
- Upload custom TTF/WOFF font files
- Configurable font size and color depth (bpp)

### 4. Emoji collection
- Preset sets of 21 basic emotions (in 32x32 and 64x64 sizes)
- Upload custom emoji images

### 5. Chat background
- Light/dark mode
- Solid color or image background
- Automatically adapts to the screen resolution

## Usage

1. Serve `index.html` through a web server
2. Select the chip model and screen configuration
3. Configure the theme elements in the different tabs
4. Click the generate button to review the resource list
5. Confirm to generate and download the `assets.bin` file

## Technical notes

- The built static assets are in the `assets/` directory
- The original models and resource files are in the `static/` directory
- Works offline with no extra dependencies

## Notes

- This tool is designed for offline use; all resources are included in this directory
- The generated `assets.bin` file must be used with the voice box hardware
- Mind the file format and size limits of custom resources to ensure compatibility