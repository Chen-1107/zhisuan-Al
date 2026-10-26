#!/usr/bin/env python3
"""
MCP模块基类 - 定义模块和节点的统一接口
"""

from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional

class MCPModule(ABC):
    """MCP模块基类"""

    def __init__(self, name: str, version: str = "1.0.0"):
        self.name = name
        self.version = version
        self._nodes = {}  # 节点注册表

    @abstractmethod
    def get_node_definitions(self) -> List[Dict[str, Any]]:
        """
        返回该模块提供的所有节点定义
        每个节点定义包含：
        - id: 唯一标识
        - name: 显示名称
        - description: 描述
        - category: 分类
        - inputs: 输入端口列表
        - outputs: 输出端口列表
        - config_schema: 配置参数schema
        """
        pass

    @abstractmethod
    async def execute_node(self, node_id: str, inputs: Dict[str, Any],
                           config: Dict[str, Any]) -> Dict[str, Any]:
        """执行节点，返回输出数据"""
        pass

    def get_node(self, node_id: str):
        return self._nodes.get(node_id)