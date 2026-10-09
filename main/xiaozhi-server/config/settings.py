import os
import asyncio
from config.config_loader import read_config, get_project_dir, load_config


default_config_file = "config.yaml"
config_file_valid = False


def check_config_file():
    global config_file_valid
    if config_file_valid:
        return
    """
    Simplified config check; only informs the user how config files are being used
    """
    custom_config_file = get_project_dir() + "data/." + default_config_file
    if not os.path.exists(custom_config_file):
        raise FileNotFoundError(
            "data/.config.yaml not found; please follow the tutorial to make sure the config file exists"
        )

    # Check whether config is read from the API
    config = asyncio.run(load_config())
    if config.get("read_config_from_api", False):
        print("Reading config from the API")
        old_config_origin = read_config(custom_config_file)
        if old_config_origin.get("selected_module") is not None:
            error_msg = "Your config file appears to contain both control panel (manager-api) config and local config:\n"
            error_msg += "\nWe recommend that you:\n"
            error_msg += "1. Copy config_from_api.yaml from the root directory into data/ and rename it to .config.yaml\n"
            error_msg += "2. Configure the API URL and secret according to the tutorial\n"
            raise ValueError(error_msg)
    config_file_valid = True
