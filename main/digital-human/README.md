This is a development document. To deploy the Xiaozhi server, [click here for the deployment guide](../../README.md#%E9%83%A8%E7%BD%B2%E6%96%87%E6%A1%A3)

For all-in-one digital human deployment, Kiosk full-screen startup and system environment setup, [click here for the all-in-one deployment guide](../../docs/all-in-one-digital-human-setup.md)

For wake word model download, runtime configuration and detailed usage, [click here for the wake word guide](../../docs/digital-human-wakeword.md)

# Overview

digital-human is a standalone digital human test module. It provides a local test page, front-end interaction resources, a wake word runtime and an event bridge, used for end-to-end testing of the whole digital human interaction pipeline.

# Quick Start

Install dependencies:

```bash
pip install -r wakeword_runtime/requirements.txt
```

Start the module:

```bash
python start.py
```

# Endpoints

After startup, the following are available:

- Page: http://127.0.0.1:8006/index.html
- Event bridge: ws://127.0.0.1:8006/wakeword-ws
- Health check: http://127.0.0.1:8006/health

# Directory Layout

- `start.py`: module startup entry
- `index.html`: digital human test page entry
- `wakeword_runtime`: local wake word runtime and configuration directory
- `js`、`css`: front-end scripts and styles of the page
- `images`、`resources`: page resource files

# Related Documents

- All-in-one deployment guide: for full deployment on x86 devices, Kiosk display and start-on-boot configuration
	[../../docs/all-in-one-digital-human-setup.md](../../docs/all-in-one-digital-human-setup.md)
- Wake word guide: for wake word model download, runtime configuration and local debugging
	[../../docs/digital-human-wakeword.md](../../docs/digital-human-wakeword.md)
