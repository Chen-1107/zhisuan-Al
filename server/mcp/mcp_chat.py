#!/usr/bin/env python3
"""
对话模块 - 用户交互、意图解析
"""

from .mcp_base import MCPModule
from typing import Dict, Any, List
import json
try:
    import ollama
except ImportError:
    ollama = None  # 环境缺失时模块仍可加载，调用处在try-except中兜底

class ChatModule(MCPModule):
    def __init__(self):
        super().__init__("Chat", "1.0.0")
        self._nodes = {
            "chat_input": {
                "name": "用户输入",
                "description": "接收用户自然语言输入",
                "category": "对话",
                "inputs": [],
                "outputs": [{"id": "text", "type": "string", "label": "用户消息"}],
                "config_schema": {}
            },
            "chat_output": {
                "name": "助手回复",
                "description": "输出AI助手回复",
                "category": "对话",
                "inputs": [{"id": "text", "type": "string", "label": "回复内容"}],
                "outputs": [],
                "config_schema": {}
            },
            "intent_parser": {
                "name": "意图解析",
                "description": "解析用户消息，提取意图和参数",
                "category": "对话",
                "inputs": [{"id": "text", "type": "string", "label": "用户消息"}],
                "outputs": [
                    {"id": "intent", "type": "string", "label": "意图"},
                    {"id": "params", "type": "object", "label": "参数"},
                    {"id": "smiles", "type": "string", "label": "提取的SMILES"}
                ],
                "config_schema": {
                    "model": {"type": "string", "default": "qwen3.5:2b", "label": "LLM模型"}
                }
            },
            "condition": {
                "name": "条件判断",
                "description": "根据条件分支执行",
                "category": "对话",
                "inputs": [{"id": "value", "type": "any", "label": "待判断值"}],
                "outputs": [
                    {"id": "true", "type": "any", "label": "真分支"},
                    {"id": "false", "type": "any", "label": "假分支"}
                ],
                "config_schema": {
                    "condition": {"type": "string", "default": "", "label": "条件表达式"}
                }
            }
        }

    def get_node_definitions(self) -> List[Dict[str, Any]]:
        return list(self._nodes.values())

    async def execute_node(self, node_id: str, inputs: Dict[str, Any],
                           config: Dict[str, Any]) -> Dict[str, Any]:
        node = self._nodes.get(node_id)
        if not node:
            raise ValueError(f"未知节点: {node_id}")

        if node_id == "chat_input":
            return {"text": inputs.get("text", "")}
        elif node_id == "chat_output":
            return {"status": "sent", "text": inputs.get("text", "")}
        elif node_id == "intent_parser":
            return await self._parse_intent(inputs.get("text", ""), config)
        elif node_id == "condition":
            return await self._evaluate_condition(inputs.get("value"), config)
        return {}

    async def _parse_intent(self, text: str, config: Dict) -> Dict:
        model = config.get("model", "qwen3.5:2b")
        prompt = f"""解析用户意图，提取参数，输出JSON格式：
用户消息：{text}
输出格式：{{"intent": "...", "params": {{}}, "smiles": ""}}
"""
        try:
            response = ollama.chat(model=model, messages=[{"role": "user", "content": prompt}])
            result = json.loads(response['message']['content'])
            return result
        except:
            return {"intent": "unknown", "params": {}, "smiles": ""}

    async def _evaluate_condition(self, value: Any, config: Dict) -> Dict:
        condition = config.get("condition", "")
        try:
            result = eval(condition, {"value": value})
            return {"true": result, "false": not result}
        except:
            return {"true": False, "false": True}