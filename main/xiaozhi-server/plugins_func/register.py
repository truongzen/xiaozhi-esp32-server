from config.logger import setup_logging
from enum import Enum

TAG = __name__

logger = setup_logging()


class ToolType(Enum):
    NONE = (1, "Do nothing else after calling the tool")
    WAIT = (2, "Call the tool and wait for the function to return")
    CHANGE_SYS_PROMPT = (3, "Modify the system prompt to switch role personality or duties")
    SYSTEM_CTL = (
        4,
        "System control that affects the normal conversation flow, e.g. exit or play music; requires the conn argument",
    )
    IOT_CTL = (5, "IoT device control; requires the conn argument")
    MCP_CLIENT = (6, "MCP client")

    def __init__(self, code, message):
        self.code = code
        self.message = message


class Action(Enum):
    ERROR = (-1, "Error")
    NOTFOUND = (0, "Function not found")
    NONE = (1, "Do nothing")
    RESPONSE = (2, "Reply directly")
    REQLLM = (3, "Request the LLM to generate a reply after calling the function")
    RECORD = (4, "Record the tool call in the conversation history without calling the LLM")

    def __init__(self, code, message):
        self.code = code
        self.message = message


class ActionResponse:
    def __init__(self, action: Action, result=None, response=None):
        self.action = action  # Action type
        self.result = result  # Result produced by the action
        self.response = response  # Content of the direct reply


class FunctionItem:
    def __init__(self, name, description, func, type):
        self.name = name
        self.description = description
        self.func = func
        self.type = type


class DeviceTypeRegistry:
    """Device type registry for managing IoT device types and their functions"""

    def __init__(self):
        self.type_functions = {}  # type_signature -> {func_name: FunctionItem}

    def generate_device_type_id(self, descriptor):
        """Generate a type ID from the device capability descriptor"""
        properties = sorted(descriptor["properties"].keys())
        methods = sorted(descriptor["methods"].keys())
        # Use the combination of properties and methods as the unique device type identifier
        type_signature = (
            f"{descriptor['name']}:{','.join(properties)}:{','.join(methods)}"
        )
        return type_signature

    def get_device_functions(self, type_id):
        """Get all functions for a device type"""
        return self.type_functions.get(type_id, {})

    def register_device_type(self, type_id, functions):
        """Register a device type and its functions"""
        if type_id not in self.type_functions:
            self.type_functions[type_id] = functions


# Initialize the function registry dict
all_function_registry = {}
# Module name -> list of function names, used to expand module-level plugin names into concrete function names
module_func_map = {}


def register_function(name, desc, type=None):
    """Decorator that registers a function into the function registry dict"""

    def decorator(func):
        all_function_registry[name] = FunctionItem(name, desc, func, type)
        # Record the module-to-function mapping, used to expand module-level plugin config
        module_name = func.__module__.split(".")[-1]
        module_func_map.setdefault(module_name, []).append(name)
        logger.bind(tag=TAG).debug(f"Function '{name}' loaded and available for registration")
        return func

    return decorator


def register_device_function(name, desc, type=None):
    """Decorator that registers a device-level function into the function registry dict"""

    def decorator(func):
        logger.bind(tag=TAG).debug(f"Device function '{name}' loaded")
        return func

    return decorator


class FunctionRegistry:
    def __init__(self):
        self.function_registry = {}
        self.logger = setup_logging()

    def register_function(self, name, func_item=None):
        # If func_item is provided, register it directly
        if func_item:
            self.function_registry[name] = func_item
            self.logger.bind(tag=TAG).debug(f"Function '{name}' registered directly")
            return func_item

        # Otherwise look it up in all_function_registry
        func = all_function_registry.get(name)
        if not func:
            self.logger.bind(tag=TAG).error(f"Function '{name}' not found")
            return None
        self.function_registry[name] = func
        self.logger.bind(tag=TAG).debug(f"Function '{name}' registered successfully")
        return func

    def unregister_function(self, name):
        # Unregister the function, checking it exists
        if name not in self.function_registry:
            self.logger.bind(tag=TAG).error(f"Function '{name}' not found")
            return False
        self.function_registry.pop(name, None)
        self.logger.bind(tag=TAG).info(f"Function '{name}' unregistered successfully")
        return True

    def get_function(self, name):
        return self.function_registry.get(name)

    def get_all_functions(self):
        return self.function_registry

    def get_all_function_desc(self):
        return [func.description for _, func in self.function_registry.items()]
